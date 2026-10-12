package cl.duocuc.controllers;

import cl.duocuc.dao.PrestamoDAO;
import cl.duocuc.model.Prestamo;
import cl.duocuc.model.ResultadoPrestamo;

import java.util.List;

/**
 * Controlador de los préstamos y las devoluciones.
 * <p>
 * Es el único punto por donde pasa el stock de los libros, y por eso sus dos
 * operaciones de escritura son <b>{@code synchronized}</b>: la vista las ejecuta
 * desde un {@code SwingWorker}, es decir en un hilo distinto al de la interfaz, y si
 * se dispararan dos a la vez podrían verificar el mismo stock antes de que la otra lo
 * descuente. Al estar sincronizadas, un préstamo entra solo cuando el anterior
 * terminó.
 * <p>
 * El {@code synchronized} protege la operación dentro de la aplicación y la
 * transacción del {@link PrestamoDAO} la protege dentro de la base de datos: la
 * verificación del stock y su modificación ocurren en la misma transacción, con la
 * fila del libro bloqueada, y terminan en {@code commit()} o en {@code rollback()}.
 * No se pisan, se complementan.
 */
public class PrestamoController {

    private final PrestamoDAO prestamoDAO = new PrestamoDAO();

    /**
     * Registra el préstamo de un libro a un estudiante: verifica el stock, inserta el
     * préstamo con la fecha de hoy, calcula la fecha de devolución a
     * {@value cl.duocuc.model.Prestamo#DIAS_DE_PRESTAMO} días y descuenta un ejemplar.
     * <p>
     * Está {@code synchronized} para que dos hilos no puedan verificar y modificar el
     * stock del mismo libro al mismo tiempo. El resultado llega como
     * {@link ResultadoPrestamo} porque el intento se puede rechazar por dos motivos
     * distintos —sin ejemplares o el estudiante ya tiene ese libro— y la vista necesita
     * distinguirlos.
     *
     * @param idEstudiante estudiante elegido en la vista
     * @param idLibro      libro elegido en la vista
     * @return cómo terminó el intento, para que la vista avise lo que corresponde
     * @throws IllegalArgumentException            si falta elegir el estudiante o el libro
     * @throws cl.duocuc.exception.PersistenciaException si la transacción falla
     */
    public synchronized ResultadoPrestamo registrarPrestamo(int idEstudiante, int idLibro) {
        Validaciones.identificador(idEstudiante, "un estudiante");
        Validaciones.identificador(idLibro, "un libro disponible");

        return prestamoDAO.registrarPrestamo(Prestamo.nuevo(idEstudiante, idLibro));
    }

    /**
     * Registra la devolución de un préstamo: lo marca como devuelto y suma un
     * ejemplar al stock del libro.
     * <p>
     * Está {@code synchronized} por el mismo motivo que el préstamo: también modifica
     * el stock, y así una devolución no puede cruzarse con un préstamo en curso.
     *
     * @param idPrestamo préstamo seleccionado en la tabla
     * @return {@code true} si quedó registrada; {@code false} si el préstamo ya estaba
     *         devuelto
     * @throws IllegalArgumentException            si no hay un préstamo seleccionado
     * @throws cl.duocuc.exception.PersistenciaException si la transacción falla
     */
    public synchronized boolean registrarDevolucion(int idPrestamo) {
        Validaciones.identificador(idPrestamo, "un préstamo pendiente");

        return prestamoDAO.registrarDevolucion(idPrestamo);
    }

    /**
     * Devuelve los préstamos que todavía no se han devuelto, que son los que se
     * muestran para registrar una devolución.
     *
     * @return los préstamos pendientes
     */
    public List<Prestamo> listarPendientes() {
        return prestamoDAO.listarPendientes();
    }

    /**
     * Devuelve todos los préstamos de un estudiante, devueltos y pendientes.
     *
     * @param idEstudiante identificador del estudiante
     * @return sus préstamos, del más reciente al más antiguo
     */
    public List<Prestamo> listarPorEstudiante(int idEstudiante) {
        return prestamoDAO.listarPorEstudiante(idEstudiante);
    }
}
