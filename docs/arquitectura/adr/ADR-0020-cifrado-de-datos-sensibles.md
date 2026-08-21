# ADR-0020 — Cifrado de datos sensibles: AES-GCM en la aplicación con HMAC indexado

**Fecha:** 2026-08-21
**Estado:** aceptada

## Contexto

RN-046 exige que la cédula, la selfie y la cuenta bancaria se guarden cifradas.
`docs/operacion/datos-personales.md` las clasifica como **sensibles** y pide
«cifrado, acceso restringido y auditado». La selfie y el documento son archivos y
ya tienen su sitio: el almacén reservado de ADR-0018. Lo que no tiene mecanismo es
lo que va en columnas de la base: **el número de documento y el número de cuenta
bancaria**.

Hoy no existe nada. Ninguna de las diecinueve ADR anteriores decide cómo se cifra
un dato en reposo, y HU-002 no se puede empezar sin eso porque decide el esquema.

Tres restricciones acotan las opciones, y la segunda es la que descarta lo obvio:

- **La clave no puede vivir en el repositorio.** Llega por variable de entorno,
  desde Secret Manager en la nube (`docs/operacion/configuracion.md`).
- **El criterio 5 de HU-002 exige unicidad: un mismo número de documento no puede
  quedar verificado en dos cuentas.** Un cifrado autenticado bien hecho produce un
  texto distinto cada vez para el mismo dato —tiene que hacerlo, o filtra qué filas
  comparten valor—, así que **la columna cifrada no se puede indexar ni comparar**.
  Buscar «¿existe ya esta cédula?» sobre datos cifrados es imposible por diseño.
- **La pantalla muestra los últimos cuatro dígitos** de la cuenta
  (`datos-personales.md`), y eso no puede exigir descifrar la fila entera en cada
  listado.

## Opciones

**A. Cifrar en la aplicación con AES-GCM, más un HMAC indexado.** La biblioteca
estándar de Java trae las dos cosas: `AES/GCM/NoPadding` y `HmacSHA256`. Sin
dependencia nueva. El cifrado va en un adaptador de `infrastructure` detrás de un
puerto, así que el dominio no sabe que existe. La unicidad se resuelve con una
columna aparte: el HMAC del número con una **clave distinta** de la de cifrado, que
sí es determinista y por tanto indexable, y no es reversible.

Costo: dos claves que gestionar en vez de una, y una columna más por dato buscable.

**B. `pgcrypto` en la base de datos.** `pgp_sym_encrypt` en el `INSERT`. Menos
código propio. El costo es donde acaba la clave: viaja **dentro de la sentencia
SQL**, así que aparece en el registro de consultas lentas, en un `EXPLAIN`, en una
traza de error del driver y en cualquier herramienta de observación del proveedor
gestionado. Con Neon o Supabase eso significa que la clave pasa por la
infraestructura de un tercero cada vez que se escribe una fila. Cifrar para que la
base no vea el dato, entregándole la clave a la base, no cifra nada.

**C. Sobre-cifrado con Cloud KMS.** La clave de datos la envuelve una clave de KMS
que nunca sale de Google; rotación y auditoría las hace el proveedor. Es lo más
robusto. Costo: una dependencia y una llamada de red más en el camino de escritura,
otra pieza que falla, y ata a GCP una parte más del sistema justo cuando ADR-0019
dejó el hospedaje sin decidir.

## Decisión

**Opción A.** Cifrado autenticado AES-256-GCM en la aplicación, con biblioteca
estándar, detrás de un puerto de `application` implementado en `infrastructure`.
Cada dato buscable lleva además un HMAC-SHA256 con clave propia, y ese es el que se
indexa.

Concretamente, para cada dato sensible en columna:

| Columna | Contenido |
|---|---|
| `<dato>_cipher` | Nonce de 12 bytes, texto cifrado y etiqueta de autenticación, en una sola cadena |
| `<dato>_key_version` | Qué clave lo cifró, para poder rotar sin reescribir todo de golpe |
| `<dato>_lookup` | HMAC-SHA256 del valor normalizado, con la clave de búsqueda. Único e indexado cuando la regla lo exige |
| `<dato>_last_four` | Los últimos cuatro dígitos, en claro |

Dos claves independientes, las dos por variable de entorno y las dos obligatorias
cuando la funcionalidad está encendida: una para cifrar y otra para el HMAC.

## Motivo

**Frente a B**, porque entregarle la clave a la base de datos anula el motivo de
cifrar. El modelo de amenaza aquí no es «alguien roba el disco»: es un volcado, un
respaldo que acaba donde no debía, o el acceso legítimo de quien administra la base
gestionada. En los tres casos, la clave dentro de la sentencia SQL está del mismo
lado que el dato.

**Frente a C**, porque hoy no aporta lo único que lo justificaría. Con A, la clave
está en Secret Manager y solo la lee la cuenta de servicio del backend; con C, está
en KMS y solo la usa la misma cuenta. La diferencia real es la rotación
automatizada y el registro de uso de la clave, y ninguna de las dos hace falta
todavía para un volumen que es cero. Lo que sí aporta hoy es una llamada de red en
cada escritura y otra atadura a un proveedor. La ADR queda abierta para envolver la
clave con KMS más adelante: el formato de las columnas ya lleva versión de clave,
así que ese cambio es un adaptador nuevo y una migración de datos, no un rediseño.

**Sobre las dos claves separadas.** Si el HMAC usara la clave de cifrado, quien
consiguiera una conseguiría las dos capacidades: descifrar y confirmar
adivinaciones. Y confirmar adivinaciones es barato aquí: una cédula colombiana es
un número de ocho a diez dígitos, o sea que el espacio se recorre entero en
segundos. Con la clave de búsqueda a salvo, el HMAC no dice nada; con la clave
filtrada, el HMAC deja de proteger y por eso no comparte suerte con el cifrado.

**Sobre `last_four` en claro.** Cuatro dígitos de una cuenta bancaria no
identifican a nadie por sí solos y es lo que la pantalla muestra de todas formas.
Guardarlos aparte evita descifrar en cada listado, que es donde un cifrado mal
puesto se convierte en la causa de que una pantalla tarde.

## Consecuencias

Lo que se gana:

- La base de datos, sus respaldos y quien la administre nunca ven el número.
- Cifrado **autenticado**: una fila modificada a mano no descifra, falla.
- Sin dependencia nueva: es biblioteca estándar de Java.
- La rotación de claves es posible sin parar nada, porque la fila dice con qué
  clave se cifró.

Lo que se acepta perder:

- **Sobre estas columnas no se puede buscar, ordenar ni filtrar**, salvo por
  igualdad exacta a través del HMAC. Nada de «documentos que empiezan por 105».
  Quien necesite eso tiene que replantear la consulta, no el cifrado.
- **Dos claves más que custodiar y rotar.** Perder la de cifrado es perder los
  datos: no hay recuperación, y eso es lo que significa cifrar bien.
- **Cuatro columnas por dato** en lugar de una. El esquema se lee peor a cambio de
  que la regla de unicidad sea posible.
- **La clave viaja en la memoria del proceso.** Un volcado de memoria del backend
  la contiene. Es el límite de cifrar en la aplicación y el precio de no
  entregársela a la base.
- El código propio de criptografía es poco —una clase— pero es criptografía, y se
  prueba por comportamiento: que descifrar devuelva lo cifrado, que dos cifrados
  del mismo valor sean distintos, que el HMAC del mismo valor sea igual, que una
  etiqueta alterada falle, y que la clave de búsqueda no sirva para descifrar.

## Cuándo revisar

- **Si aparece un requisito de rotación automática o de auditoría de uso de la
  clave**, entra la opción C envolviendo la clave de datos con KMS. El formato con
  versión de clave está pensado para que ese día no haya que reescribir el esquema.
- **Si hace falta buscar por algo más que igualdad exacta.** Entonces la
  conversación no es de cifrado sino de qué dato se necesita realmente en claro, y
  la respuesta más probable es que no se necesita.
- **Si un dato sensible nuevo no es un número corto.** El HMAC funciona aquí porque
  el valor es exacto y normalizable. Para un nombre o una dirección no sirve, y esa
  columna no se podrá buscar de ninguna manera.
