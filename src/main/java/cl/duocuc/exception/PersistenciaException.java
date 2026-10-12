package cl.duocuc.exception;

/**
 * Error ocurrido al leer o escribir en la base de datos.
 * <p>
 * Los DAO ya no muestran mensajes con {@code JOptionPane}: cuando una operación
 * JDBC falla, capturan la {@link java.sql.SQLException} y la vuelven a lanzar
 * envuelta en esta excepción, con un mensaje entendible para el usuario. Así el
 * DAO queda desacoplado de Swing y el mensaje lo muestra la vista, que es la
 * capa de presentación.
 * <p>
 * Es una excepción no comprobada para que los controladores puedan dejarla pasar
 * sin cambiar sus firmas: el flujo sigue siendo
 * vista → controlador → DAO, y el aviso vuelve por el mismo camino.
 */
public class PersistenciaException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que se mostrará al usuario.
     *
     * @param mensaje descripción entendible del problema
     */
    public PersistenciaException(String mensaje) {
        super(mensaje);
    }

    /**
     * Crea la excepción con el mensaje que se mostrará al usuario y el error
     * original de JDBC.
     *
     * @param mensaje descripción entendible del problema
     * @param causa   excepción original lanzada por JDBC
     */
    public PersistenciaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
