# Estrategia de pruebas

Una prueba existe para poder cambiar el código sin miedo. Si no da confianza o
se rompe cada vez que se refactoriza algo interno, sobra.

## Reparto

```
Extremo a extremo   pocas, caminos críticos de negocio
Integración         las necesarias, todo lo que cruza un límite real
Unitarias           muchas, rápidas, sin infraestructura
```

## Backend

**Unitarias.** `domain` y `application`. JUnit 5, sin Spring, sin base de datos.
Cada regla de negocio con identificador `RN-xxx` tiene al menos una prueba que lo
menciona en el nombre. Cobertura mínima 90% en `domain`.

**Integración.** `infrastructure`. Testcontainers con PostgreSQL 17.
Prohibido H2: se comporta distinto a PostgreSQL y da falsa confianza. Se prueban
repositorios, migraciones, cifrado de columnas y clientes HTTP contra un servidor
simulado.

**De aplicación.** `@SpringBootTest` solo en `bootstrap` y solo para verificar
que el cableado funciona y que los caminos completos responden. Pocas.

**Nombres.** En español, describiendo comportamiento:

```java
@Test
void deberia_bloquear_la_cuenta_tras_cinco_intentos_fallidos_RN_006()
```

**Datos.** Constructores de prueba (`UserBuilder.unVendedorVerificado()`), no
SQL repetido. Cada prueba crea lo suyo y no depende del orden de ejecución.

## Frontend

**Unitarias.** Vitest mediante `@angular/build:unit-test`. Karma y Jasmine no
están en el proyecto. `domain` y `application` se prueban sin TestBed.

**Componentes.** Se prueba lo que el usuario observa: qué se ve, qué pasa al
interactuar, qué se anuncia. Consultas por rol accesible y texto visible antes
que por selector CSS. Nunca se prueban métodos privados.

**HTTP.** Siempre simulado. Ninguna prueba unitaria sale a la red.

**Extremo a extremo.** Playwright sobre los caminos críticos: registro,
verificación, ingreso, publicación, compra. Se ejecutan contra el entorno de
desarrollo en cada integración a la rama principal.

**Accesibilidad.** Verificación automática con axe sobre WCAG 2.2 AA en todas
las páginas públicas y en los dos modos, claro y oscuro:
`frontend/e2e/accesibilidad.spec.ts`, decidido en ADR-0016. Más revisión manual
de teclado en cada componente nuevo. Un fallo de contraste o de foco rompe la
construcción igual que un error de compilación.

Ninguna regla de axe se desactiva para que la suite pase: una violación se
corrige en el código. Y una página pública nueva entra a la lista de rutas
auditadas, porque lo que no está en esa lista no se audita y nadie se entera.
Conviene recordar que un motor automático encuentra una parte de los problemas,
no todos: la suite en verde no significa que el sitio sea accesible.

**Cobertura.** Mínimo 80% global y 90% en `domain` y `application`.

## Qué no se prueba

- Configuración de framework que no contiene lógica propia. Por eso la clase de
  arranque `SastraApplication` está excluida de la medición de cobertura: que el
  cableado funciona lo demuestra `ApplicationContextTest` levantando el contexto
  completo, no un porcentaje.
- Getters, setters y mapeadores triviales sin reglas.
- Estilos visuales, salvo el contraste y el foco, que sí son requisitos.

## Reglas

1. Una prueba falla por una sola razón.
2. Si una prueba es inestable, se arregla o se elimina el mismo día. Una prueba
   que a veces falla enseña al equipo a ignorar los fallos.
3. Un error reportado se reproduce primero con una prueba que falle, y luego se
   corrige.
4. Ninguna prueba se marca como omitida para que el build pase.
5. Toda prueba nueva debe fallar al menos una vez antes de darse por buena. Si
   nunca la viste en rojo, no sabes qué está verificando.
