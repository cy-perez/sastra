# Árbol de categorías

**Estado: aprobado el 24 de agosto de 2026.** Cierra la decisión que
`alcance.md` listaba como aplazada desde el comienzo del proyecto y desbloquea
HU-007, que sin árbol no se podía implementar.

Este archivo es la fuente de verdad de la taxonomía. Agregar o retirar una
categoría se hace aquí primero y en una migración después; nunca al revés.

## Cómo se decidió la forma

- **El primer nivel es el tipo de producto, no el género.** El glosario ya dice
  que «Dama» y «Caballero» no son categorías del proyecto. Un árbol por género se
  duplica entero y obliga a clasificar por a quién se supone que le queda, que es
  una pregunta que la prenda no responde.
- **Dos niveles: familia y categoría.** La categoría hoja es la que declara talla,
  medidas y qué condiciones admite. Tres niveles hoy serían categorías vacías y un
  menú más pesado en móvil, que es donde se publica y se compra.
- **Entran ropa, calzado, accesorios y tecnología**, que es exactamente lo que
  RN-024 permite vender. La tecnología se agregó el 24 de agosto de 2026 y solo
  se vende nueva (RN-064).

## Dos cosas que cambiaron de lo decidido en HU-007

Aparecieron al dibujar el árbol, no antes. Las dos están aplicadas ya en
`modelo-datos.md` y en HU-007.

**Una categoría admite más de un sistema de talla.** Sin eje de género, «Jeans» es
una sola categoría y en Colombia se venden en talla numérica y en pulgadas de
cintura. Con `categories.size_system` en singular no se puede expresar, y partir
la categoría en dos por eso sería meter el género por la puerta de atrás. La
categoría declara **la lista de sistemas admisibles** y el vendedor elige uno;
`products.size_system` sigue guardando cuál eligió, tal como estaba. Cambió
`categories.size_system` por `categories.size_systems`, y nada más.

**El grupo `ACCESSORY` no le sirve a todos los accesorios.** Alto, ancho y
profundidad describen un bolso y no describen una correa ni una bufanda. Se
partió en dos:

| Código | Visible | Medidas obligatorias | Para |
|---|---|---|---|
| `ACCESSORY_VOLUME` | Accesorio con volumen | Alto, ancho, profundidad | Bolsos, mochilas |
| `ACCESSORY_FLAT` | Accesorio plano | Largo, ancho | Correas, bufandas, gafas, joyería |

Y un tercero para la familia de tecnología, `DEVICE`, con alto, ancho y
profundidad. Mide lo mismo que `ACCESSORY_VOLUME` y se separa igual, porque lo
que un grupo significa no es su lista de medidas: es a qué se le pueden pedir. El
día que la tecnología necesite pulgadas de pantalla, se le agregan a `DEVICE` sin
tocar los bolsos.

Queda una arruga conocida y se anota en vez de taparse: **sombreros y gorras**
miden por contorno, no por largo y ancho. Van en `ACCESSORY_FLAT` porque tendido
en plano es como los fotografía y los mide quien vende de segunda, y porque un
grupo entero para una categoría no se justifica todavía. Si aparece volumen real
de sombrerería, se crea el grupo.

## El árbol

Los códigos son estables y en inglés; el nombre visible sale por Transloco en los
dos idiomas. `slug` es lo que va en la URL del catálogo.

### 1. Parte superior — `tops`

Grupo de medida: `TOP` (pecho, largo, hombros, largo de manga).

| Categoría | `slug` | Sistemas de talla |
|---|---|---|
| Camisetas | `camisetas` | `ALPHA` |
| Camisas y blusas | `camisas-y-blusas` | `ALPHA`, `NUMERIC_CO` |
| Tops y bodies | `tops-y-bodies` | `ALPHA` |
| Suéteres, buzos y sacos | `sueteres-y-buzos` | `ALPHA` |
| Blazers | `blazers` | `ALPHA`, `NUMERIC_CO` |
| Chaquetas y abrigos | `chaquetas-y-abrigos` | `ALPHA` |

### 2. Parte inferior — `bottoms`

Grupo de medida: `BOTTOM` (cintura, cadera, tiro, largo).

| Categoría | `slug` | Sistemas de talla |
|---|---|---|
| Jeans | `jeans` | `WAIST_INCHES`, `NUMERIC_CO` |
| Pantalones | `pantalones` | `WAIST_INCHES`, `NUMERIC_CO` |
| Shorts y bermudas | `shorts-y-bermudas` | `WAIST_INCHES`, `NUMERIC_CO` |
| Faldas | `faldas` | `ALPHA`, `NUMERIC_CO` |
| Leggings y deportivos | `leggings-y-deportivos` | `ALPHA` |

### 3. Prenda entera — `full-body`

Grupo de medida: `FULL_BODY` (pecho, cintura, cadera, largo).

| Categoría | `slug` | Sistemas de talla |
|---|---|---|
| Vestidos | `vestidos` | `ALPHA`, `NUMERIC_CO` |
| Enterizos y overoles | `enterizos-y-overoles` | `ALPHA`, `NUMERIC_CO` |
| Trajes de baño | `trajes-de-bano` | `ALPHA` |

### 4. Calzado — `footwear`

Grupo de medida: `FOOTWEAR` (largo de plantilla interna).

| Categoría | `slug` | Sistemas de talla |
|---|---|---|
| Tenis y deportivos | `tenis` | `FOOTWEAR_CO` |
| Zapatos formales | `zapatos-formales` | `FOOTWEAR_CO` |
| Botas y botines | `botas-y-botines` | `FOOTWEAR_CO` |
| Sandalias | `sandalias` | `FOOTWEAR_CO` |

### 5. Tecnología — `tech`

Grupo de medida: `DEVICE` (alto, ancho, profundidad). Sistema de talla:
`ONE_SIZE` en todas, porque un dispositivo no tiene talla.

**Solo admite condición nueva** (RN-064). Es la única familia con esa
restricción, y es lo que separa a Sendik de un mercado de segunda genérico.

| Categoría | `slug` | Grupo de medida |
|---|---|---|
| Celulares y tabletas | `celulares-y-tabletas` | `DEVICE` |
| Computadores y portátiles | `computadores` | `DEVICE` |
| Televisores y monitores | `televisores-y-monitores` | `DEVICE` |
| Audio | `audio` | `DEVICE` |
| Consolas y videojuegos | `consolas-y-videojuegos` | `DEVICE` |
| Cámaras | `camaras` | `DEVICE` |
| Accesorios de tecnología | `accesorios-de-tecnologia` | `DEVICE` |

**Las especificaciones no están decididas y no se inventan aquí.** Pulgadas de
pantalla, capacidad de almacenamiento, memoria, modelo: son lo que un comprador
de tecnología mira antes que nada, y hoy no hay ningún campo que las guarde. Alto,
ancho y profundidad sirven para cotizar el envío y poco más. Mientras no se
decida, esos datos viven en la descripción, que es texto libre y no se puede
filtrar. Es una carencia real y está anotada abajo.

### 6. Accesorios — `accessories`

| Categoría | `slug` | Grupo de medida | Sistemas de talla |
|---|---|---|---|
| Bolsos y mochilas | `bolsos-y-mochilas` | `ACCESSORY_VOLUME` | `ONE_SIZE` |
| Correas y cinturones | `correas` | `ACCESSORY_FLAT` | `ALPHA`, `ONE_SIZE` |
| Bufandas y pañoletas | `bufandas-y-panoletas` | `ACCESSORY_FLAT` | `ONE_SIZE` |
| Sombreros y gorras | `sombreros-y-gorras` | `ACCESSORY_FLAT` | `ALPHA`, `ONE_SIZE` |
| Gafas | `gafas` | `ACCESSORY_FLAT` | `ONE_SIZE` |
| Joyería y relojes | `joyeria-y-relojes` | `ACCESSORY_FLAT` | `ONE_SIZE` |

Seis familias y treinta y una categorías. Es la lista con la que se puede abrir un
catálogo, no la lista definitiva: crecer es agregar filas, y agregar una fila no
toca código.

**La familia decide qué condición admite la categoría.** Moda —las cinco primeras
familias— admite las cuatro condiciones; tecnología solo nueva. Eso se guarda en
la categoría, en `allows_used`, y lo comprueba el dominio: no es una validación de
formulario que se pueda saltar llamando al endpoint (RN-064).

## Lo que esto implica

- **Las categorías son datos, no código.** Van en una migración de Flyway que las
  siembra, igual que las entidades financieras de `V7__financial_institutions.sql`
  y por el mismo motivo: son muchas, van a crecer y el catálogo necesita su código
  estable. Ninguna enumeración de Java lista categorías.
- **`categories.active` es lo que permite retirar una** sin tocar las
  publicaciones que ya la tienen. Una categoría retirada no se puede elegir en
  borradores nuevos y no se reasigna nada de forma automática.
- **Los nombres visibles de las categorías sí viven en la tabla**, en `name_es` y
  `name_en`, y los traduce el servidor según `Accept-Language`. Es la excepción
  que el proyecto ya tenía escrita: `contrato-api.md` dice que la cabecera de
  idioma «solo afecta contenido que el sistema traduce, como nombres de
  categoría». La regla de que ningún texto visible se escribe en el código sigue
  intacta —una fila de base de datos no es código— y aplicarla aquí obligaría a
  desplegar el frontend cada vez que se agrega una categoría, que es justo lo que
  se evita al tratarlas como datos.

## Lo que falta

- **Las especificaciones de tecnología**: pulgadas, capacidad, memoria, modelo.
  Es lo primero que mira quien compra un dispositivo y hoy no hay dónde guardarlo.
  Sin eso, el catálogo de tecnología se puede abrir pero no se puede filtrar por
  nada que le importe a un comprador.
- **Los valores concretos de cada sistema de talla**, que HU-007 dejó anotados
  como pendientes de confirmar con alguien que venda ropa en Colombia. No bloquea:
  corregirlos no toca el esquema.
- **Los nombres visibles de las treinta y una categorías en los dos idiomas**, que
  van en la migración que las siembra. El español está en las tablas de arriba; el
  inglés hay que escribirlo, y manda que se entienda, no que traduzca literal.
