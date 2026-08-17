# ADR-0010 — Ocho tomas a 45 grados y proporción 3:4 para el visor

**Fecha:** 2026-08-15 · **Estado:** aceptada

## Contexto

La especificación inicial pedía cuatro fotos obligatorias (frontal, laterales y
posterior) y, con ellas, una quinta visualización giratoria. Por otro lado, el
kit de interfaz fija la proporción 3:4 como no negociable para toda foto de
producto, mientras que la especificación del visor sugería forzar 1:1.

Dos conflictos que hay que resolver antes de construir.

## Decisión

**Ocho tomas a 45 grados**, de las cuales las de 0, 90, 180 y 270 grados son las
cuatro canónicas del carrusel. **Proporción 3:4 para todas**, incluidas las del
visor.

## Motivo

**Sobre el número de tomas.** Cuatro fotogramas no producen una rotación: producen
un salto de 90 grados que se percibe como un carrusel defectuoso. El efecto de
giro empieza a funcionar alrededor de los 8 fotogramas y se vuelve fluido hacia
los 12 o 16. Como cada toma adicional es fricción real para un vendedor que
fotografía desde el celular, ocho es el punto de equilibrio: el giro se lee como
giro y el flujo sigue siendo tolerable.

Que las cuatro canónicas salgan de la misma secuencia evita pedir fotos dos
veces y garantiza que el carrusel y el visor muestren exactamente la misma prenda
bajo la misma luz.

**Sobre la proporción.** El kit de interfaz es explícito: si cada foto llega con
su propia proporción, la rejilla del catálogo queda con tarjetas de alturas
distintas. Mantener 3:4 en el visor significa un solo canal de imágenes, un solo
almacenamiento, y ningún salto visual al pasar del carrusel al giro. Una prenda
colgada, además, es más alta que ancha: 3:4 la encuadra mejor que 1:1.

## Consecuencias

- El asistente de captura tiene ocho pasos, con progreso visible.
- Se mide el abandono en este paso. Si es alto, se baja a seis tomas antes que a
  cuatro; nunca por debajo de seis.
- Las publicaciones con menos de ocho tomas no ofrecen visor, solo carrusel.
- El recorte a 3:4 se hace en el cliente antes de subir, y el backend lo verifica
  al confirmar: nunca se confía en lo que declara el cliente.

## Cuándo revisar

Con datos reales de abandono en el asistente de captura, o si se mide que el
visor no mejora la conversión frente al carrusel.
