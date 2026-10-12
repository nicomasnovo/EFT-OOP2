package cl.duocuc.model;

/**
 * Datos comunes a todas las personas que registra la biblioteca. Es una clase
 * abstracta: no existe una "persona" suelta en el sistema, siempre es un
 * {@link Usuario} que inicia sesión o un {@link Estudiante} que pide libros.
 * <p>
 * Reúne los cuatro campos que ambas tablas comparten ({@code id}, {@code nombre},
 * {@code rut} y {@code correo}) y obliga a sus subclases a describirse mediante
 * {@link #getDescripcion()}. Esa descripción es el punto de polimorfismo: la
 * interfaz la pide siempre igual y cada subclase responde con lo suyo.
 * <p>
 * La herencia existe solo en Java. En MySQL siguen siendo dos tablas
 * independientes, {@code usuarios} y {@code estudiantes}, tal como las define el
 * script oficial.
 */
public abstract class Persona {

    private int id;
    private String nombre;
    private String rut;
    private String correo;

    /**
     * Crea una persona con los datos comunes.
     *
     * @param id     identificador en la base de datos; {@code 0} si todavía no se guardó
     * @param nombre nombre completo
     * @param rut    RUT con guion, por ejemplo {@code 12345678-9}
     * @param correo correo electrónico
     */
    protected Persona(int id, String nombre, String rut, String correo) {
        this.id = id;
        this.nombre = nombre;
        this.rut = rut;
        this.correo = correo;
    }

    /**
     * Describe a la persona para mostrarla en la interfaz. Cada subclase agrega la
     * información que la distingue.
     *
     * @return el texto que la representa en pantalla
     */
    public abstract String getDescripcion();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    /**
     * Devuelve la descripción polimórfica, de modo que los {@code JComboBox} y las
     * listas de Swing muestren a la persona sin tener que preguntar su tipo.
     *
     * @return el resultado de {@link #getDescripcion()}
     */
    @Override
    public String toString() {
        return getDescripcion();
    }
}
