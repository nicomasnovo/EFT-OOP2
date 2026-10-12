package cl.duocuc.conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Punto único de conexión con MySQL, implementado con el patrón <b>Singleton</b>.
 * <p>
 * La configuración JDBC (URL, usuario y contraseña) está escrita una sola vez y en
 * un solo lugar: no hay forma de crear otra instancia, porque el constructor es
 * privado y la única manera de obtener el objeto es {@link #getInstance()}, que es
 * {@code synchronized} para que dos hilos —por ejemplo el de Swing y el
 * {@code SwingWorker} que registra un préstamo— no alcancen a construir dos
 * instancias distintas.
 * <p>
 * El Singleton guarda la configuración, no la conexión: cada llamada a
 * {@link #obtenerConexion()} abre una conexión nueva que el DAO cierra con
 * try-with-resources. Así dos operaciones simultáneas no comparten una misma
 * {@link Connection}, que no es segura entre hilos y además perdería el control de
 * la transacción de los préstamos.
 * <p>
 * Los DAO son los únicos que piden conexiones. El resto de la aplicación no conoce
 * esta clase, salvo {@code Main}, que al arrancar valida la base de datos con
 * {@link #validarConexion()}.
 */
public class DatabaseConnection {

    /**
     * Base de datos {@code biblioteca} creada por el script oficial. Se declara la
     * codificación UTF-8 porque la tabla {@code usuarios} tiene la columna
     * {@code contraseña} y los datos poblados traen tildes y eñes.
     */
    private static final String URL =
            "jdbc:mysql://localhost:3306/biblioteca?useUnicode=true&characterEncoding=UTF-8";

    private static final String USUARIO = "root";
    private static final String CONTRASENA = "MysqlDuoc#2026";

    /** Única instancia de la clase; se crea la primera vez que alguien la pide. */
    private static DatabaseConnection instance;

    /**
     * Constructor privado: impide que otra clase cree instancias con {@code new} y
     * es lo que obliga a pasar por {@link #getInstance()}.
     */
    private DatabaseConnection() {
    }

    /**
     * Devuelve la única instancia de la clase, creándola la primera vez.
     * <p>
     * Es {@code synchronized} porque la aplicación trabaja con varios hilos: si dos
     * llegaran juntos con {@code instance} todavía en {@code null}, ambos la
     * construirían y el Singleton dejaría de ser único.
     *
     * @return la instancia compartida de la conexión
     */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Abre una conexión nueva con la base de datos {@code biblioteca}.
     *
     * @return la conexión abierta; quien la pide debe cerrarla
     * @throws SQLException si la base de datos no responde o rechaza las credenciales
     */
    public Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CONTRASENA);
    }

    /**
     * Comprueba que la base de datos esté disponible antes de mostrar el login.
     * Reutiliza {@link #obtenerConexion()}, por lo que no duplica la configuración
     * JDBC, e informa el resultado por consola.
     *
     * @return {@code true} si se pudo abrir una conexión utilizable
     */
    public boolean validarConexion() {
        try (Connection conn = obtenerConexion()) {
            boolean valida = conn != null && !conn.isClosed();
            if (valida) {
                System.out.println("Conexión exitosa a la base de datos 'biblioteca'.");
            } else {
                System.err.println("Error al conectar con la base de datos: "
                        + "la conexión no quedó abierta.");
            }
            return valida;
        } catch (SQLException e) {
            System.err.println("Error al conectar con la base de datos 'biblioteca': "
                    + e.getMessage());
            return false;
        }
    }
}
