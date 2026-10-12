package cl.duocuc.model;

/**
 * Persona que inicia sesión en el sistema. Corresponde a una fila de la tabla
 * {@code usuarios} y agrega a {@link Persona} lo que distingue a quien se
 * autentica: su contraseña y su {@link Rol}.
 * <p>
 * El rol es lo que la ventana principal consulta para decidir qué módulos
 * habilita, de modo que el control de acceso queda en el modelo y no repartido por
 * las vistas.
 */
public class Usuario extends Persona {

    private String contrasena;
    private Rol rol;

    /**
     * Crea un usuario con sus credenciales y su perfil.
     *
     * @param id         identificador en la tabla {@code usuarios}
     * @param nombre     nombre completo
     * @param rut        RUT con guion
     * @param correo     correo con que inicia sesión
     * @param contrasena contraseña guardada en la columna {@code contraseña}
     * @param rol        perfil de acceso
     */
    public Usuario(int id, String nombre, String rut, String correo, String contrasena, Rol rol) {
        super(id, nombre, rut, correo);
        this.contrasena = contrasena;
        this.rol = rol;
    }

    /**
     * Indica si el usuario puede administrar la biblioteca, es decir, crear,
     * modificar y eliminar libros, categorías, estudiantes y préstamos.
     *
     * @return {@code true} si su rol es {@link Rol#BIBLIOTECARIO}
     */
    public boolean esBibliotecario() {
        return rol == Rol.BIBLIOTECARIO;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    /**
     * Describe al usuario por su nombre y su perfil, que es lo que interesa ver en
     * la barra de sesión de la ventana principal.
     *
     * @return por ejemplo {@code "Antonia Pérez (Bibliotecario)"}
     */
    @Override
    public String getDescripcion() {
        return getNombre() + " (" + rol.getEtiqueta() + ")";
    }
}
