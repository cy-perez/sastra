# ADR-0009 — Hospedaje escalonado: Vercel primero, GCP después

**Fecha:** 2026-08-15 · **Estado:** sustituida por ADR-0019

> Lo que sigue es lo que se decidió el 15 de agosto de 2026 y se conserva sin
> editar. **Ya no aplica:** Vercel quedó descartado y el despliegue se aplazó hasta
> tener el proyecto completo, con lo cual desapareció la etapa de prototipo
> hospedada y el escalonamiento se quedó sin nada que escalonar. La decisión vigente
> está en ADR-0019.

## Contexto

El destino final es Google Cloud Platform, pero durante el desarrollo del
prototipo no hay dominio comprado, no hay usuarios y no tiene sentido pagar
infraestructura de producción.

## Decisión

Dos etapas explícitas.

**Etapa de prototipo (ahora):** frontend en Vercel con su capa gratuita, backend
en Cloud Run con escalado a cero, base de datos en Neon o Supabase con su capa
gratuita, imágenes en Cloud Storage, correos con la capa gratuita de un
proveedor transaccional. Costo cercano a cero.

**Etapa de lanzamiento:** todo en GCP. Frontend en Cloud Run tras un balanceador
con CDN, base de datos en Cloud SQL, secretos en Secret Manager, dominio
`sastra.co` con certificado administrado.

## Motivo

Cloud Run escala a cero: sin tráfico, no cobra. Eso permite tener el backend
desplegado desde el primer día sin costo apreciable, y además significa que la
plataforma de destino se usa desde el comienzo en lugar de descubrir sus
particularidades el día del lanzamiento.

Cloud SQL, en cambio, cobra por hora encendida aunque nadie lo use: es lo primero
que conviene aplazar. Una base gestionada gratuita durante el prototipo evita ese
gasto sin cambiar una línea de código, porque en ambos casos es PostgreSQL.

Vercel para el frontend porque el despliegue de una aplicación Angular con
renderizado en servidor es inmediato y las vistas previas por rama facilitan
revisar avances desde el celular. Se migra a Cloud Run al lanzar, para tener todo
bajo un mismo proveedor y una sola factura.

## Condición

La migración solo es barata si la aplicación no se ata a nada específico de
Vercel: sin funciones propias del proveedor, sin su almacenamiento, sin sus
variables mágicas. Todo lo externo entra por variable de entorno.

## Consecuencias

- Dos configuraciones de despliegue conviviendo un tiempo.
- La cadena de conexión a la base cambia entre etapas; nada más lo hace.
- Detalles y costos estimados en `docs/operacion/entornos.md`.

## Cuándo revisar

Al comprar el dominio y fijar fecha de lanzamiento.
