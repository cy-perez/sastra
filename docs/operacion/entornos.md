# Entornos y despliegue

Dos entornos: `dev` y `prod`. Sin entorno intermedio: con un solo desarrollador,
un tercer entorno es sobre todo mantenimiento sin beneficio.

## Etapa actual: `dev` en pie, `prod` todavía no

El 26 de agosto de 2026 se contrató **`sendik.co` en GoDaddy**, y con eso se cerró
la única decisión que quedaba abierta: dónde se hospeda el sitio. La respuesta está
en ADR-0024 y es **Cloud Run, junto al backend**; GoDaddy queda como registrador y
servidor de DNS, que es lo que mejor hace.

**Lo que cambia:** `dev` deja de ser una etapa aplazada y pasa a estar desplegado.
Las dos piezas van a Cloud Run desde el mismo commit verificado, con el mismo flujo
(`despliegue.yml`) y en la misma región.

**Lo que no cambia:** producción sigue esperando, y por el mismo motivo de siempre.
Un despliegue de producción pide atención continua —secretos que rotar, respaldos
que verificar, alertas que atender— y arrastra las piezas que sí cuestan: la
instancia mínima siempre activa y Cloud SQL, que cobra por hora encendida aunque
nadie lo use. Nada de eso hace falta para tener `dev` en pie.

**Y `dev` sigue costando cero.** No es una concesión: es consecuencia de que todo
lo que lo compone escala a cero o entra en capa gratuita.

| Pieza | Dónde | Costo en `dev` |
|---|---|---|
| Dominio `sendik.co` | GoDaddy, registrador y DNS | Ya pagado |
| Frontend Angular SSR | Cloud Run, escalado a cero (ADR-0024) | 0 |
| Backend Spring Boot | Cloud Run, escalado a cero | 0 |
| Certificado de `dev.sendik.co` | Gestionado por Cloud Run | 0 |
| PostgreSQL | Neon o Supabase, capa gratuita | 0 |
| Imágenes | Cloud Storage, capa gratuita 5GB | 0 |
| Secretos | Secret Manager | 0 en capa gratuita |
| Correo transaccional | Resend o Brevo, capa gratuita | 0 |
| Registro y métricas | Cloud Logging, capa gratuita | 0 |
| Errores del frontend | Sentry, capa gratuita | 0 |
| Repositorio y CI | GitHub Actions, minutos gratuitos | 0 |

**El alojamiento compartido de GoDaddy no aparece en la tabla porque no ejecuta
nada.** Está pagado y sin usar, y esa es una consecuencia asumida en ADR-0024: no
puede servir un sitio con renderizado en servidor, y montarlo encima costaría más
—en configuración frágil y en tiempo de operación— que el propio plan.

**Primer arranque en frío.** Con escalado a cero, la primera petición tras un
periodo inactivo tarda varios segundos, y ahora son dos servicios los que arrancan.
Para `dev` es aceptable y es justo lo que lo mantiene en cero. Al lanzar se
configura una instancia mínima siempre activa en las dos piezas.

## Etapa de lanzamiento

| Pieza | Servicio | Costo mensual estimado |
|---|---|---|
| Frontend y backend | Cloud Run, mínimo 1 instancia cada uno (ADR-0024) | 15 a 40 USD |
| Base de datos | Cloud SQL PostgreSQL 17, la más pequeña | 25 a 50 USD |
| Almacenamiento e imágenes | Cloud Storage y CDN | 5 a 15 USD |
| Balanceador y certificado | Load Balancer con certificado administrado. **Solo si hace falta**: el mapeo de dominios de Cloud Run ya da certificado gestionado sin costo, y en `dev` es lo que se usa | 0 a 25 USD |
| Búsqueda | Typesense administrado o en instancia propia | 0 a 25 USD |
| Correo | Plan de pago según volumen | 0 a 20 USD |
| Dominio | `sendik.co` en GoDaddy, ya contratado | Anual |

Rango realista de arranque: 60 a 150 USD al mes. Cifras orientativas de agosto de
2026; hay que confirmarlas con la calculadora de precios antes de comprometer
presupuesto.

**Región:** `us-east1`. Frente a `southamerica-east1`, la latencia hacia Colombia
es similar o mejor y los precios son bastante más bajos. Si más adelante aparece
un requisito de residencia de datos, se revisa.

## Cómo se despliega

Todo por integración continua. Nadie despliega desde su máquina.

> **Estado a septiembre de 2026.** El flujo está escrito completo y cubre las dos
> piezas: `.github/workflows/verificacion.yml` compila, prueba y analiza en cada
> pull request y en cada integración a `main`, y `despliegue.yml` publica **el
> backend y el frontend** en `dev` con cada integración a `main`, y en `prod` con
> etiqueta de versión y aprobación manual. El despliegue **llama** a la
> verificación en lugar de repetir sus pasos, así que nada se publica sin pasarla
> entera.
>
> Los dos trabajos se omiten mientras no exista `GCP_PROJECT_ID`, para que la
> canalización diga la verdad —no hay dónde desplegar— en vez de quedarse roja y
> dejar de leerse.
>
> **`dev` está en pie desde el 2 de septiembre de 2026**, y con eso deja de ser
> teoría: `https://dev.sendik.co` y `https://api-dev.sendik.co` responden con
> certificado gestionado y siguen costando cero. Las cuentas que hacían falta —el
> proyecto de Google Cloud, la base gestionada, los secretos, la federación de
> identidades y el DNS— están creadas, y lo que costó cada una quedó anotado en
> `despliegue.md`.
>
> Lo que falta ahora es **`prod`**, que es otra vuelta a esa misma lista.

```
rama de trabajo -> pull request -> verificación -> main -> dev automático
                                                       -> prod con aprobación manual
```

- `dev` se despliega en cada integración a `main`.
- `prod` requiere una etiqueta de versión y una aprobación explícita.
- Las migraciones de base de datos corren antes de arrancar la nueva versión y
  deben ser compatibles hacia atrás: una versión anterior de la aplicación tiene
  que poder seguir funcionando durante el despliegue.
- Toda migración destructiva se hace en dos pasos separados por al menos un
  despliegue: primero se deja de usar la columna, después se elimina.

## Retorno a una versión anterior

- Aplicación: se redirige el tráfico a la revisión previa de Cloud Run. Es
  inmediato.
- Base de datos: no se revierte una migración. Se corrige hacia adelante con una
  migración nueva. Por eso las migraciones destructivas van en dos pasos.

## Respaldos

- Copia diaria automática con retención de 7 días en `dev` y de 30 días en `prod`.
- Una restauración de prueba antes del lanzamiento, y luego cada trimestre. Un
  respaldo que nunca se ha restaurado no es un respaldo.

## Vigilancia

Alertas que deben existir antes de abrir al público:

- Tasa de errores 5xx por encima del 1% durante 5 minutos.
- Latencia del percentil 95 por encima de 2 segundos.
- Fallo de la verificación de estado de salud.
- Errores en el procesamiento de eventos de la pasarela.
- Uso de base de datos por encima del 80%.
- Gasto acumulado del mes por encima del presupuesto definido.

La última no es menor: un error de configuración en la nube puede costar dinero
real en horas.
