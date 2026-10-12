package cl.duocuc.controllers;

import cl.duocuc.dao.PrestamoDAO;
import cl.duocuc.model.Reporte;

/**
 * Controlador del módulo de reportes. Coordina los tres informes que pide la
 * biblioteca y los devuelve como {@link Reporte}, listos para volcarse en un
 * {@code JTable}.
 * <p>
 * Las consultas con {@code JOIN}, {@code GROUP BY} y {@code COUNT} están en el
 * {@link PrestamoDAO}: aquí no hay una línea de SQL, igual que en el resto de los
 * controladores.
 */
public class ReporteController {

    private final PrestamoDAO prestamoDAO = new PrestamoDAO();

    /**
     * Reporte de los libros más prestados de la biblioteca.
     *
     * @return el ranking de libros con la cantidad de veces que salió cada uno
     */
    public Reporte librosMasPrestados() {
        return prestamoDAO.librosMasPrestados();
    }

    /**
     * Reporte con el historial de préstamos de un estudiante.
     *
     * @param idEstudiante estudiante elegido en la vista
     * @return su historial, del préstamo más reciente al más antiguo
     * @throws IllegalArgumentException si no hay un estudiante seleccionado
     */
    public Reporte historialEstudiante(int idEstudiante) {
        Validaciones.identificador(idEstudiante, "un estudiante");
        return prestamoDAO.historialEstudiante(idEstudiante);
    }

    /**
     * Reporte de los libros que están prestados en este momento, con el estudiante que
     * tiene cada uno y la fecha en que debe devolverlo.
     *
     * @return los libros que están fuera de la biblioteca
     */
    public Reporte librosActualmentePrestados() {
        return prestamoDAO.librosPrestados();
    }
}
