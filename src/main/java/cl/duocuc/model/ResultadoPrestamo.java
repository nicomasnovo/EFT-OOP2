package cl.duocuc.model;

/**
 * En qué terminó el intento de registrar un préstamo.
 * <p>
 * Existe porque un {@code boolean} no alcanza: la operación puede rechazarse por dos
 * motivos distintos que el usuario necesita distinguir, y los dos se descubren
 * <b>dentro de la transacción</b>, con las filas ya bloqueadas, no antes. Comprobarlos
 * antes de abrir la transacción sería inútil: entre la comprobación y la escritura
 * otro préstamo podría cambiar justo lo que se acaba de revisar.
 * <p>
 * El DAO devuelve uno de estos valores y la vista elige el mensaje. Así la capa de
 * datos no escribe textos para el usuario y la vista no interpreta números.
 */
public enum ResultadoPrestamo {

    /** El préstamo quedó guardado y el ejemplar descontado del stock. */
    REGISTRADO,

    /**
     * El libro se quedó sin ejemplares disponibles entre que la interfaz lo ofreció y
     * el momento de guardar.
     */
    SIN_EJEMPLARES,

    /**
     * El estudiante ya tiene ese mismo libro prestado y todavía no lo devuelve, así
     * que no corresponde entregarle otro ejemplar.
     */
    YA_LO_TIENE_PRESTADO;

    /**
     * Indica si el préstamo quedó efectivamente registrado.
     *
     * @return {@code true} solo para {@link #REGISTRADO}
     */
    public boolean fueRegistrado() {
        return this == REGISTRADO;
    }
}
