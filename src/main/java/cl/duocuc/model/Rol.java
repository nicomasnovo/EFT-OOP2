package cl.duocuc.model;

/**
 * Perfiles con que un {@link Usuario} puede entrar al sistema. Son exactamente los
 * dos valores del {@code ENUM} de la columna {@code usuarios.rol} en el script
 * oficial: {@code bibliotecario} y {@code estudiante}.
 * <p>
 * El rol decide qué módulos muestra la ventana principal: el bibliotecario
 * administra toda la biblioteca y el estudiante solo consulta.
 */
public enum Rol {

    /** Perfil con acceso completo: libros, categorías, estudiantes y préstamos. */
    BIBLIOTECARIO("bibliotecario", "Bibliotecario"),

    /** Perfil de consulta: catálogo de libros y sus propios préstamos. */
    ESTUDIANTE("estudiante", "Estudiante");

    private final String valorEnBD;
    private final String etiqueta;

    Rol(String valorEnBD, String etiqueta) {
        this.valorEnBD = valorEnBD;
        this.etiqueta = etiqueta;
    }

    /**
     * Devuelve el texto con que el rol se guarda en MySQL.
     *
     * @return el valor tal como lo espera el {@code ENUM} de la tabla
     */
    public String getValorEnBD() {
        return valorEnBD;
    }

    /**
     * Devuelve el texto con que el rol se muestra al usuario.
     *
     * @return la etiqueta para la interfaz
     */
    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el valor leído de la base de datos en un rol.
     *
     * @param texto contenido de la columna {@code rol}
     * @return el rol correspondiente
     * @throws IllegalArgumentException si el texto no corresponde a ningún rol
     */
    public static Rol desdeTexto(String texto) {
        if (texto != null) {
            for (Rol rol : values()) {
                if (rol.valorEnBD.equalsIgnoreCase(texto.trim())) {
                    return rol;
                }
            }
        }
        throw new IllegalArgumentException("Rol no reconocido: " + texto);
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
