import { deflateSync } from 'node:zlib';

/**
 * Construye un PNG valido del tamano que se pida.
 *
 * <p>Hace falta uno **de verdad**, no un archivo con la firma de PNG y basura
 * detras: el servidor lo decodifica y lo vuelve a codificar para quitarle el EXIF
 * (ADR-0018), asi que un archivo falso se rechaza y la prueba no llegaria a
 * comprobar nada. Y hace falta poder pedir las dimensiones, porque el minimo de
 * pixeles es una de las cosas que se validan.
 *
 * <p>Se construye a mano en lugar de guardar un archivo en el repositorio, y en
 * lugar de traer una libreria para generarlo. Un archivo binario en el repositorio
 * no dice de que tamano es sin abrirlo, y aqui el tamano es justo el parametro que
 * importa; una libreria seria una dependencia de pruebas para cuarenta lineas de
 * formato que no van a cambiar.
 *
 * <p>El formato: firma, IHDR con el tamano, IDAT con las filas comprimidas y IEND.
 * Cada bloque lleva su longitud, su tipo y su CRC32.
 */
const FIRMA = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/** Tabla de CRC32 del propio formato PNG. Se calcula una vez. */
const TABLA_CRC = (() => {
  const tabla = new Uint32Array(256);
  for (let byte = 0; byte < 256; byte++) {
    let valor = byte;
    for (let vuelta = 0; vuelta < 8; vuelta++) {
      valor = valor & 1 ? 0xedb88320 ^ (valor >>> 1) : valor >>> 1;
    }
    tabla[byte] = valor >>> 0;
  }
  return tabla;
})();

function crc32(datos: Buffer): number {
  let acumulado = 0xffffffff;
  for (const byte of datos) {
    acumulado = TABLA_CRC[(acumulado ^ byte) & 0xff] ^ (acumulado >>> 8);
  }
  return (acumulado ^ 0xffffffff) >>> 0;
}

function bloque(tipo: string, datos: Buffer): Buffer {
  const longitud = Buffer.alloc(4);
  longitud.writeUInt32BE(datos.length);

  const conTipo = Buffer.concat([Buffer.from(tipo, 'ascii'), datos]);

  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(conTipo));

  return Buffer.concat([longitud, conTipo, crc]);
}

/**
 * @param ancho pixeles
 * @param alto pixeles
 * @returns un PNG de color verdadero, relleno de un solo color
 */
export function pngDe(ancho: number, alto: number): Buffer {
  const cabecera = Buffer.alloc(13);
  cabecera.writeUInt32BE(ancho, 0);
  cabecera.writeUInt32BE(alto, 4);
  cabecera.writeUInt8(8, 8); // 8 bits por canal
  cabecera.writeUInt8(2, 9); // color verdadero, RGB sin alfa
  cabecera.writeUInt8(0, 10); // compresion, el unico valor que existe
  cabecera.writeUInt8(0, 11); // filtrado, idem
  cabecera.writeUInt8(0, 12); // sin entrelazado

  // Cada fila empieza con su byte de filtro, aqui siempre 0: sin filtrar.
  const bytesPorFila = ancho * 3;
  const crudo = Buffer.alloc(alto * (bytesPorFila + 1));
  for (let fila = 0; fila < alto; fila++) {
    const inicio = fila * (bytesPorFila + 1);
    crudo[inicio] = 0;
    crudo.fill(0x7f, inicio + 1, inicio + 1 + bytesPorFila);
  }

  return Buffer.concat([
    FIRMA,
    bloque('IHDR', cabecera),
    bloque('IDAT', deflateSync(crudo)),
    bloque('IEND', Buffer.alloc(0)),
  ]);
}
