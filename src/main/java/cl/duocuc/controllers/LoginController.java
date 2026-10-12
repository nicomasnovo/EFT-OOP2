package cl.duocuc.controllers;

import cl.duocuc.dao.EstudianteDAO;
import cl.duocuc.dao.UsuarioDAO;
import cl.duocuc.model.Estudiante;
import cl.duocuc.model.Usuario;

/**
 * Controlador del inicio de sesión. Recibe el correo y la contraseña que se
 * escribieron en el {@code LoginView}, pide al {@link UsuarioDAO} que los valide
 * contra la tabla {@code usuarios} y devuelve la sesión con el rol del usuario.
 * <p>
 * No hay ninguna credencial escrita en el código: todos los usuarios vienen del
 * script de poblado.
 * <p>
 * Cuando el rol es {@code estudiante}, busca además su ficha en la tabla
 * {@code estudiantes} por RUT. Las dos tablas son independientes en el script
 * oficial, así que el RUT —que es {@code UNIQUE} en ambas— es lo que las vincula.
 * Esa ficha es la que permite mostrarle sus propios préstamos y nada más.
 */
public class LoginController {

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final EstudianteDAO estudianteDAO = new EstudianteDAO();

    /**
     * Sesión abierta en la aplicación.
     *
     * @param usuario usuario autenticado, con su rol
     * @param ficha   ficha de {@code estudiantes} del usuario, o {@code null} si es
     *                bibliotecario o si su RUT no está registrado como estudiante
     */
    public record Sesion(Usuario usuario, Estudiante ficha) {

        /**
         * Indica si la sesión puede administrar la biblioteca.
         *
         * @return {@code true} si el usuario es bibliotecario
         */
        public boolean puedeAdministrar() {
            return usuario.esBibliotecario();
        }
    }

    /**
     * Valida las credenciales y abre la sesión.
     *
     * @param correo     correo escrito en el formulario
     * @param contrasena contraseña escrita en el formulario
     * @return la sesión abierta, o {@code null} si las credenciales no son correctas
     * @throws IllegalArgumentException            si algún campo viene vacío
     * @throws cl.duocuc.exception.PersistenciaException si la base de datos falla
     */
    public Sesion autenticar(String correo, String contrasena) {
        String correoLimpio = Validaciones.requerido(correo, "correo");
        String claveLimpia = Validaciones.requerido(contrasena, "contraseña");

        Usuario usuario = usuarioDAO.autenticar(correoLimpio, claveLimpia);
        if (usuario == null) {
            return null;
        }

        Estudiante ficha = usuario.esBibliotecario()
                ? null
                : estudianteDAO.buscarPorRut(usuario.getRut());

        return new Sesion(usuario, ficha);
    }
}
