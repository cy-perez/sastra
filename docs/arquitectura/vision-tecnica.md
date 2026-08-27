# Visión técnica

## Panorama

```
Navegador
   |
   |  HTML renderizado en servidor + hidratación
   v
Angular 21 SSR  (Cloud Run, ADR-0024)
   |
   |  REST /api/v1  ·  JWT  ·  JSON
   v
Spring Boot 4.1  (Cloud Run)
   |
   +--> PostgreSQL 17        datos transaccionales
   +--> Cloud Storage        imágenes de producto y verificación
   +--> Wompi                recaudo y división de pago
   +--> Typesense            búsqueda (Fase 3)
   +--> Transportadoras      cotización de envíos (Fase 3)
   +--> Proveedor de correo  transaccionales
```

## Las cuatro capas

La regla es una sola: **las dependencias apuntan hacia adentro**. El dominio no
sabe que existe una base de datos, ni un navegador, ni HTTP.

```
        presentation
              |
              v
        application  ---- define puertos ---->  (interfaces)
              |                                      ^
              v                                      |
          domain                              infrastructure
                                              implementa puertos
```

**domain.** El corazón. Entidades, objetos de valor, eventos, invariantes,
excepciones de negocio. Sin anotaciones, sin librerías, sin nada que se pueda
reemplazar. Si mañana cambia la base de datos, el marco web o la pasarela, esta
capa no se toca. Se prueba con JUnit puro en milisegundos.

**application.** Los casos de uso. Orquesta el dominio, abre transacciones,
publica eventos y declara **puertos de salida**: `UserRepository`, `MailSender`,
`PublicFileStore` y `RestrictedFileStore`; con las fases siguientes llegan
`PaymentGateway` y los demás. Son interfaces que esta capa define según lo que
necesita, no según lo que la tecnología ofrece.

Que los archivos sean **dos** puertos y no uno con un parámetro de visibilidad es
el ejemplo de esa frase: la capa necesita distinguir «guarda esto para que
cualquiera lo vea» de «guarda esta cédula», y con un solo puerto esa diferencia
quedaría a un argumento de distancia (ADR-0018).

**infrastructure.** Los adaptadores. Implementa cada puerto contra la tecnología
real: repositorios con Spring Data JDBC, cliente de Cloud Storage, cliente HTTP de
Resend y, en Fase 3, el de Wompi. Aquí vive todo lo sucio y todo lo reemplazable.
Cada puerto de archivos tiene dos adaptadores —sistema de archivos y Cloud
Storage— y los elige una variable, no un despliegue.

**presentation.** El borde. Controladores REST del lado backend, componentes y
rutas del lado Angular. Traduce entre el mundo exterior y los casos de uso. No
decide nada de negocio.

### Cómo se verifica que nadie la rompe

1. **Gradle.** `domain` no declara dependencias de framework; el módulo
   literalmente no puede importar Spring.
2. **ArchUnit.** Pruebas que fallan si aparece un import prohibido, si un
   controlador llama a un repositorio, o si una entidad de tabla se filtra a la
   API.
3. **Subagente revisor.** `.claude/agents/arquitecto.md` revisa cada
   cambio contra estas reglas.

Las tres capas de defensa son deliberadas: la primera evita el error, la segunda
lo detecta, la tercera lo explica.

## Contextos

El código se organiza por contexto de negocio, no por tipo de artefacto. Dentro
de cada contexto se repiten las cuatro capas.

| Contexto | Responsabilidad | Fase |
|---|---|---|
| `identity` | Cuentas, credenciales, sesiones, verificación de vendedor | 1 y 2 |
| `catalog` | Prendas, publicaciones, imágenes, moderación | 2 |
| `search` | Indexación y consulta del catálogo | 3 |
| `order` | Pedidos y su ciclo de vida | 3 |
| `payment` | Intentos de pago, división, desembolsos | 3 |
| `shipping` | Cotización, guías y seguimiento | 3 |
| `shared` | Objetos de valor comunes: dinero, identificadores, fechas | 1 |

Un contexto no llama al repositorio de otro. Si necesita algo, es por un caso de
uso público o por un evento de dominio. Esto mantiene abierta la puerta a
separar servicios más adelante, sin pagar hoy el costo de hacerlo.

## Comunicación entre contextos

- Dentro del mismo proceso, por eventos de dominio publicados tras confirmar la
  transacción.
- El estado de un contexto no se consulta leyendo las tablas de otro. Nunca.
- Ejemplo: cuando `payment` confirma un pago, publica `PaymentApproved`;
  `catalog` marca la prenda vendida y `order` avanza el pedido. Ninguno de los
  tres conoce a los otros dos.

## Flujo de una petición

```
POST /api/v1/auth/register
  |
  presentation   RegisterRequest -> valida formato -> RegisterSellerCommand
  |
  application    RegisterSellerUseCase
  |                 - pide el puerto UserRepository
  |                 - construye User (el dominio valida las invariantes)
  |                 - guarda, publica UserRegistered
  |
  domain         User, Email, Password: se niegan a existir en estado inválido
  |
  infrastructure UserJdbcRepository escribe en PostgreSQL
                 EmailSenderAdapter envía la verificación
```

Ningún objeto de dominio cruza hacia afuera. `presentation` devuelve su propio
DTO.

## Errores

- El dominio lanza excepciones de negocio con significado:
  `EmailAlreadyRegisteredException`, no `IllegalStateException`.
- Un manejador global en `presentation` las traduce a `ProblemDetail` con el
  código HTTP y el código de error del catálogo definido en `contrato-api.md`.
- El frontend nunca muestra un mensaje del backend directamente: recibe un código
  y lo traduce con Transloco. Así los mensajes existen en ES y EN.

## Configuración

Nada quemado en el código. Todo por variable de entorno, tipado y validado al
arrancar. Si falta una variable obligatoria, la aplicación no arranca: es
preferible a descubrirlo en producción. Ver `docs/operacion/configuracion.md`.

## Deuda aceptada conscientemente

| Decisión | Costo que se acepta | Cuándo se revisa |
|---|---|---|
| Monolito modular en un solo despliegue | Escala vertical al principio | Cuando un contexto necesite escalar solo |
| Mapeo manual entre dominio y tablas | Más código repetitivo | Si el volumen lo hace insostenible |
| Sin caché distribuida | Más consultas a la base | Cuando la latencia lo exija |
| TanStack Query en estado experimental | Rupturas posibles al actualizar | Cuando el adaptador de Angular sea estable |
