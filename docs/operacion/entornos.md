# Entornos y despliegue

Dos entornos: `dev` y `prod`. Sin entorno intermedio: con un solo desarrollador,
un tercer entorno es sobre todo mantenimiento sin beneficio.

## Etapa actual: prototipo

**Nada de esto está desplegado todavía, y es una decisión, no un pendiente.** El
despliegue del sitio —dominio y hospedaje— se hace cuando el proyecto esté lo más
completo posible. Hasta entonces se trabaja y se prueba en local, integrado contra
los servicios de GCP que hagan falta **en su capa gratuita**: son cuentas reales
contra las que se prueba de verdad, no simulaciones, y cuestan cero. Los servicios
de pago —el dominio `sendik.co`, la instancia mínima siempre activa, Cloud SQL, el
balanceador— se contratan justo antes del lanzamiento inicial.

El motivo es el costo de tener algo en pie que nadie usa. Un despliegue vivo pide
atención continua —secretos que rotar, respaldos que verificar, alertas que
atender, dependencias que actualizar en un artefacto publicado— y con un solo
desarrollador esa atención sale del tiempo de construir el producto. Además, el
mes en que empieza a contar el dominio y la instancia mínima es el mes en que
empieza el gasto, y conviene que sea el mes en que hay algo que mostrar.

Lo que esto **no** significa: no significa aplazar la canalización ni el flujo de
despliegue, que están escritos y probados (`despliegue.md`), ni probar contra
imitaciones de los servicios de la nube. La tabla siguiente es dónde vivirá cada
pieza y lo que costará cuando se ponga en pie.

| Pieza | Dónde | Costo |
|---|---|---|
| Frontend Angular SSR | Sin hospedaje: se ejecuta en local (ADR-0019) | 0 |
| Backend Spring Boot | Cloud Run, escalado a cero | Prácticamente 0 |
| PostgreSQL | Neon o Supabase, capa gratuita | 0 |
| Imágenes | Cloud Storage, capa gratuita 5GB | 0 |
| Secretos | Secret Manager | 0 en capa gratuita |
| Correo transaccional | Resend o Brevo, capa gratuita | 0 |
| Registro y métricas | Cloud Logging, capa gratuita | 0 |
| Errores del frontend | Sentry, capa gratuita | 0 |
| Repositorio y CI | GitHub Actions, minutos gratuitos | 0 |

Cloud Run escala a cero: sin tráfico no cobra. Por eso el backend **podría** estar
desplegado desde el primer día sin costo; que no lo esté es la decisión de arriba,
tomada por el trabajo de operación que arrastra y no por el precio. Cloud SQL, en cambio, cobra por hora encendida
aunque nadie lo use, y es lo primero que conviene aplazar.

**Primer arranque en frío.** Con escalado a cero, la primera petición tras un
periodo inactivo tarda varios segundos. Para el prototipo es aceptable. Al
lanzar se configura una instancia mínima siempre activa.

## Etapa de lanzamiento

| Pieza | Servicio | Costo mensual estimado |
|---|---|---|
| Frontend y backend | Cloud Run, mínimo 1 instancia | 15 a 40 USD |
| Base de datos | Cloud SQL PostgreSQL 17, la más pequeña | 25 a 50 USD |
| Almacenamiento e imágenes | Cloud Storage y CDN | 5 a 15 USD |
| Balanceador y certificado | Load Balancer con certificado administrado | 18 a 25 USD |
| Búsqueda | Typesense administrado o en instancia propia | 0 a 25 USD |
| Correo | Plan de pago según volumen | 0 a 20 USD |
| Dominio | `sendik.co` | Anual |

Rango realista de arranque: 60 a 150 USD al mes. Cifras orientativas de agosto de
2026; hay que confirmarlas con la calculadora de precios antes de comprometer
presupuesto.

**Región:** `us-east1`. Frente a `southamerica-east1`, la latencia hacia Colombia
es similar o mejor y los precios son bastante más bajos. Si más adelante aparece
un requisito de residencia de datos, se revisa.

## Cómo se despliega

Todo por integración continua. Nadie despliega desde su máquina.

> **Estado a agosto de 2026.** El flujo está escrito completo:
> `.github/workflows/verificacion.yml` compila, prueba y analiza en cada pull
> request y en cada integración a `main`, y `despliegue.yml` publica el backend en
> `dev` con cada integración a `main` y en `prod` con etiqueta de versión y
> aprobación manual. Solo el backend: el frontend no tiene trabajo de despliegue
> mientras no haya proveedor de hospedaje (ADR-0019). El despliegue **llama** a la verificación en lugar de repetir sus pasos,
> así que nada se publica sin pasarla entera.
>
> Lo que falta no es código. Para el backend son las cuentas: el proyecto de
> Google Cloud, la base gestionada y los secretos en Secret Manager, con el
> procedimiento en orden y una sola vez en `despliegue.md`. Para el frontend falta
> además elegir dónde: el hospedaje del sitio se contrata con el dominio y su
> proveedor está por definir (ADR-0019). Hasta entonces no hay nada desplegado.

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

- Copia diaria automática con retención de 7 días en la etapa de prototipo y de
  30 días en producción.
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
