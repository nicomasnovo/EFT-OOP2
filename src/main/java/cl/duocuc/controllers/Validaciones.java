package cl.duocuc.controllers;

/**
 * Comprobaciones que los controladores aplican a los datos que llegan desde los
 * formularios, antes de pasarlos a un DAO.
 * <p>
 * Están reunidas aquí para que las cinco vistas no repitan la misma validación y
 * para que los mensajes de error sean siempre los mismos. Cuando un dato no sirve se
 * lanza una {@link IllegalArgumentException} con el texto que la vista muestra en un
 * {@code JOptionPane}: la validación es regla de negocio, así que vive en la capa de
 * controladores y no en Swing.
 */
final class Validaciones {

    /**
     * Clase de utilidades: no se instancia.
     */
    private Validaciones() {
    }

    /**
     * Comprueba que un campo obligatorio venga con contenido.
     *
     * @param valor texto escrito por el usuario
     * @param campo nombre del campo, para armar el mensaje de error
     * @return el texto sin espacios sobrantes
     * @throws IllegalArgumentException si el campo viene vacío
     */
    static String requerido(String valor, String campo) {
        String limpio = valor == null ? "" : valor.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio.");
        }
        return limpio;
    }

    /**
     * Comprueba que el RUT venga con contenido y tenga una forma razonable: números,
     * guion y dígito verificador.
     *
     * @param valor RUT escrito por el usuario
     * @return el RUT sin espacios sobrantes y con el dígito verificador en mayúscula
     * @throws IllegalArgumentException si viene vacío o con un formato que la base de
     *                                 datos no podría guardar
     */
    static String rut(String valor) {
        String limpio = requerido(valor, "RUT").toUpperCase();
        if (!limpio.matches("\\d{7,8}-[\\dK]")) {
            throw new IllegalArgumentException(
                    "El RUT debe tener el formato 12345678-9 (sin puntos y con guion).");
        }
        return limpio;
    }

    /**
     * Comprueba que el correo venga con contenido y tenga una forma válida básica:
     * texto, arroba, dominio y punto.
     *
     * @param valor correo escrito por el usuario
     * @return el correo sin espacios sobrantes
     * @throws IllegalArgumentException si viene vacío o no parece un correo
     */
    static String correo(String valor) {
        String limpio = requerido(valor, "correo");
        if (!limpio.matches("[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}")) {
            throw new IllegalArgumentException(
                    "El correo no tiene un formato válido, por ejemplo nombre@correo.cl.");
        }
        return limpio;
    }

    /**
     * Convierte el stock escrito en el formulario a número y comprueba que no sea
     * negativo, porque la biblioteca no puede tener menos de cero ejemplares.
     *
     * @param valor stock escrito por el usuario
     * @return el stock como número entero
     * @throws IllegalArgumentException si no es un número o es negativo
     */
    static int stock(String valor) {
        String limpio = requerido(valor, "stock");
        int cantidad;
        try {
            cantidad = Integer.parseInt(limpio);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El stock debe ser un número entero.", e);
        }
        if (cantidad < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo.");
        }
        return cantidad;
    }

    /**
     * Comprueba que se haya seleccionado un registro antes de operar sobre él.
     *
     * @param id      identificador seleccionado, o {@code 0} si no hay selección
     * @param entidad nombre de lo que se debía seleccionar, para el mensaje de error
     * @return el identificador recibido
     * @throws IllegalArgumentException si el identificador no es válido
     */
    static int identificador(int id, String entidad) {
        if (id <= 0) {
            throw new IllegalArgumentException("Debe seleccionar " + entidad + ".");
        }
        return id;
    }
}
