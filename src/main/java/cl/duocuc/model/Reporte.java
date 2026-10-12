package cl.duocuc.model;

import java.util.List;

/**
 * Resultado de una consulta de reporte: los títulos de las columnas y las filas
 * que trajo la base de datos.
 * <p>
 * Los reportes agrupan y cruzan tablas, así que su resultado no es una lista de
 * libros ni de préstamos; es una tabla con forma propia. Al devolverla con sus
 * encabezados, el DAO decide qué se consulta y la vista solo la
 * vuelca en un {@code JTable} sin saber de SQL. Las consultas viven en el
 * {@code PrestamoDAO}, porque los tres reportes parten de la tabla
 * {@code prestamos}.
 *
 * @param columnas encabezados de la tabla, en el orden en que se muestran
 * @param filas    filas del resultado; cada arreglo trae un valor por columna
 */
public record Reporte(String[] columnas, List<Object[]> filas) {

    /**
     * Indica si la consulta no trajo ninguna fila.
     *
     * @return {@code true} si el reporte está vacío
     */
    public boolean estaVacio() {
        return filas.isEmpty();
    }

    /**
     * Devuelve las filas en el arreglo bidimensional que espera
     * {@code DefaultTableModel}.
     *
     * @return las filas como matriz
     */
    public Object[][] comoMatriz() {
        return filas.toArray(new Object[0][]);
    }
}
