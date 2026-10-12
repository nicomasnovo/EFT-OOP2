package cl.duocuc.controllers;

/**
 * Conjunto de controladores que usa la aplicación, creados una sola vez al arrancar.
 * <p>
 * Existe para que las vistas no construyan controladores: {@code Main} los crea con
 * {@link #crear()} y los va pasando del {@code LoginView} a la {@code MainView} y de
 * ahí a cada módulo. Así la separación del MVC se mantiene en una sola dirección
 * —vista → controlador → DAO— y todos los módulos trabajan con las mismas instancias.
 *
 * @param login       controlador del inicio de sesión
 * @param libros      controlador del módulo de libros
 * @param categorias  controlador del módulo de categorías
 * @param estudiantes controlador del módulo de estudiantes
 * @param prestamos   controlador de préstamos y devoluciones
 * @param reportes    controlador del módulo de reportes
 */
public record Controladores(LoginController login,
                            LibroController libros,
                            CategoriaController categorias,
                            EstudianteController estudiantes,
                            PrestamoController prestamos,
                            ReporteController reportes) {

    /**
     * Crea los controladores de la aplicación.
     *
     * @return el conjunto listo para repartir entre las vistas
     */
    public static Controladores crear() {
        return new Controladores(
                new LoginController(),
                new LibroController(),
                new CategoriaController(),
                new EstudianteController(),
                new PrestamoController(),
                new ReporteController());
    }
}
