package cl.duocuc.concurrencia;

import cl.duocuc.exception.PersistenciaException;

import java.util.concurrent.*;

/**
 * Pequeño servicio de hilos que la aplicación usa cuando necesita lanzar
 * <b>varias consultas independientes al mismo tiempo</b>.
 * <p>
 * No reemplaza al {@code SwingWorker}. El {@code SwingWorker} saca una operación del
 * hilo de la interfaz, pero la ejecuta de a una; este {@link ExecutorService} sirve
 * para el caso distinto: cuando una misma pantalla necesita dos consultas que no
 * dependen entre sí —por ejemplo la lista de estudiantes y la de libros disponibles
 * del formulario de préstamos— y esperar una después de la otra sería el doble de
 * tiempo. Se envían las dos con {@link #enviar(Callable)} y se esperan con
 * {@link #esperar(Future)}.
 * <p>
 * El pool se crea una sola vez y sus hilos son <b>daemon</b>: no impiden que la
 * máquina virtual termine cuando el usuario cierra la ventana, así que la aplicación
 * no necesita apagarlo a mano al salir.
 * <p>
 * Las tareas se envían siempre desde un hilo de segundo plano, nunca desde el de
 * Swing, porque {@link #esperar(Future)} bloquea hasta que la consulta responde.
 */
public final class Tareas {

    /**
     * Hilos que ejecutan las consultas en paralelo. Son daemon y llevan un nombre
     * propio para que se reconozcan si alguna vez hay que mirar una traza de error.
     */
    private static final ExecutorService POOL = Executors.newCachedThreadPool(tarea -> {
        Thread hilo = new Thread(tarea, "biblioteca-consulta");
        hilo.setDaemon(true);
        return hilo;
    });

    /**
     * Clase de utilidad: no se instancia.
     */
    private Tareas() {
    }

    /**
     * Envía una consulta al pool y devuelve de inmediato, sin esperar su resultado.
     *
     * @param consulta consulta que se quiere ejecutar en otro hilo
     * @param <T>      tipo del resultado de la consulta
     * @return el {@link Future} con el que después se recoge el resultado
     */
    public static <T> Future<T> enviar(Callable<T> consulta) {
        return POOL.submit(consulta);
    }

    /**
     * Espera el resultado de una consulta enviada con {@link #enviar(Callable)}.
     * <p>
     * Si la consulta falló, vuelve a lanzar su excepción original —normalmente la
     * {@link PersistenciaException} del DAO— en vez de la {@link ExecutionException}
     * con que el {@code Future} la envuelve. Así el error llega a la vista igual que
     * si la consulta se hubiera hecho sin paralelismo y se muestra con el mismo
     * mensaje.
     *
     * @param tarea tarea enviada previamente al pool
     * @param <T>   tipo del resultado de la consulta
     * @return el resultado de la consulta
     * @throws PersistenciaException si la consulta se interrumpió o falló por un
     *                               motivo que no es una excepción propia de la
     *                               aplicación
     */
    public static <T> T esperar(Future<T> tarea) {
        try {
            return tarea.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PersistenciaException("La consulta quedó interrumpida.", e);

        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof RuntimeException propia) {
                throw propia;
            }
            throw new PersistenciaException("Error al consultar la base de datos.",
                    causa == null ? e : causa);
        }
    }
}
