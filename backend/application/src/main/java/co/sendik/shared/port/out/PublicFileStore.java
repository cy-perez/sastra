package co.sendik.shared.port.out;

import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.NormalizedImage;
import java.net.URI;

/**
 * El almacen de lo que cualquiera puede ver: fotos de perfil y tomas de producto.
 *
 * <p><strong>Separado de {@link RestrictedFileStore} a proposito.</strong> Podria
 * ser un solo puerto con un parametro de visibilidad, y seria menos codigo. Pero
 * entonces la diferencia entre publicar la foto de una prenda y publicar la cedula
 * de alguien quedaria a un argumento de distancia, y ese error no deberia poder
 * escribirse (ADR-0018). Con dos tipos, "guarda esta cedula donde cualquiera la
 * vea" no se puede ni expresar.
 *
 * <p>Aqui no se valida nada. Lo que llega es una {@link NormalizedImage}, y para
 * existir ya paso por la politica: tipo comprobado por contenido, tamano, minimo de
 * dimensiones y EXIF fuera.
 */
public interface PublicFileStore {

    /**
     * Guarda la imagen y devuelve la clave con la que quedo.
     *
     * <p>La clave la decide el almacen, no quien llama: es lo que garantiza que sea
     * opaca y que no se derive del nombre original ni de nada de la persona.
     *
     * @param carpeta agrupa por clase de archivo, por ejemplo {@code avatares}
     */
    FileKey guardar(String carpeta, NormalizedImage imagen);

    /**
     * Borra el archivo. <strong>No falla nunca.</strong>
     *
     * <p>Ni si el archivo ya no existe ni si el almacen esta caido. Quien borra aqui
     * suele estar limpiando —al cambiar de foto se borra la anterior— y en ese punto
     * la operacion que importa ya salio bien: la foto nueva esta puesta y la persona
     * la ve. Propagar el fallo la convertiria en un error para quien no hizo nada
     * mal.
     *
     * <p>El adaptador registra el archivo que quedo suelto, que es lo que permite
     * limpiarlo despues. Es tambien la razon de que el registro este ahi y no en el
     * caso de uso: esta capa no declara dependencias de registro
     * (backend/CLAUDE.md).
     */
    void borrar(FileKey clave);

    /**
     * La direccion desde la que se sirve el archivo.
     *
     * <p>La compone el almacen y no el borde, porque depende de donde este guardado
     * y de que dominio lo sirva, y las dos cosas son configuracion: la capa de
     * presentacion no la ve (docs/arquitectura/vision-tecnica.md). Y no se guarda en
     * la base: si se guardara, cambiar de CDN obligaria a reescribir la tabla
     * (ADR-0018).
     */
    URI direccionDe(FileKey clave);
}
