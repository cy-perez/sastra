# ADR-0005 — Wompi con división de pago

**Fecha:** 2026-08-15 · **Estado:** aceptada, con riesgo abierto

## Contexto

El comprador paga, el vendedor recibe su parte y Sendik retiene el 5%. La
plataforma **no** debe custodiar dinero de terceros: hacerlo implica obligaciones
regulatorias que un emprendimiento en fase inicial no puede asumir, y menos
operando como persona natural.

## Decisión

Wompi como recaudador, con división del pago en la propia pasarela. Medios:
PSE, Nequi, tarjeta débito y crédito, Bancolombia a la mano y Addi. Sin
contraentrega.

## Motivo

Wompi pertenece al Grupo Bancolombia, tiene la mayor cobertura de medios locales
y Addi como financiación, que en moda tiene un efecto real sobre la conversión.
La división en la pasarela mantiene el dinero fuera de las cuentas de Sendik:
llega a cada destinatario directamente y la plataforma solo recibe su comisión.

## Riesgo abierto

**Antes de escribir una sola línea de integración hay que confirmar con Wompi:**

1. Si su producto para plataformas permite división de pago y dispersión a
   terceros, y bajo qué figura contractual.
2. Qué requisitos de vinculación exigen a cada vendedor persona natural, y qué
   parte de ese proceso se puede hacer desde Sendik.
3. Si un comercio operado por persona natural puede acceder a ese esquema.
4. Cuál es el flujo de retención y liberación disponible, para poder cumplir la
   promesa de "el pago queda retenido" que ya está en la interfaz.
5. Si se puede **reintegrar al comprador desde el dinero todavía retenido**, sin
   que haya pasado por el vendedor, y si el reintegro puede revertir también la
   comisión. RN-054 y RN-055 dependen de ello: son las reglas que hacen que el
   respaldo no dependa de que el vendedor colabore. Si la pasarela solo permite
   reversar una transacción ya dispersada, la promesa del sitio informativo se
   cae y hay que reescribirla antes de publicarla, no después.

Si la respuesta a la primera o la tercera es negativa, las alternativas son
Mercado Pago con su modelo de marketplace, o un esquema de dispersión posterior
que exigiría revisar toda la premisa de no custodiar dinero. **No se avanza en
Fase 3 sin cerrar este punto.**

## Consecuencias

- La integración se aísla tras el puerto `PaymentGateway`. Cambiar de proveedor
  no toca el dominio.
- Todo evento del proveedor se verifica por firma y se procesa de forma
  idempotente con su identificador único.
- El estado real del pago se consulta siempre contra la pasarela, nunca se
  confía en la redirección del navegador.
- La vinculación de cada vendedor es un paso más de la verificación.

## Cuándo revisar

Al cerrar la conversación con Wompi, o si los costos por transacción cambian de
forma significativa.
