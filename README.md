![Duoc UC](https://www.duoc.cl/wp-content/uploads/2022/09/logo-0.png)

# 📚 Sistema de Gestión de Biblioteca Escolar

Aplicación de escritorio desarrollada en Java para gestionar una biblioteca escolar. Permite autenticar usuarios, administrar libros, categorías y estudiantes, registrar préstamos y devoluciones y consultar reportes.

El proyecto utiliza Swing para la interfaz gráfica, JDBC para la conexión con MySQL y una arquitectura basada en MVC, DAO y Singleton.

## 🎯 Funcionalidades

- Autenticación de usuarios según rol.
- Gestión de libros.
- Gestión de categorías.
- Gestión de estudiantes.
- Registro de préstamos y devoluciones.
- Control automático del stock disponible.
- Cálculo de fecha de devolución a 7 días.
- Reporte de libros más prestados.
- Historial de préstamos por estudiante.
- Listado de libros actualmente en préstamo.

## 🛠️ Tecnologías

| Tecnología | Uso |
| --- | --- |
| Java 17 | Desarrollo de la aplicación |
| Swing / AWT | Interfaz gráfica |
| JDBC | Acceso a la base de datos |
| MySQL | Persistencia de datos |
| MySQL Connector/J | Driver JDBC |
| Maven | Gestión del proyecto y dependencias |
| SwingWorker | Ejecución de operaciones en segundo plano |
| ExecutorService | Ejecución concurrente de consultas independientes |

## 🏛️ Arquitectura

El proyecto aplica el patrón **MVC** para separar responsabilidades:

```text
Vista → Controlador → DAO → DatabaseConnection → MySQL
```

- **Model:** representa las entidades del sistema.
- **View:** contiene las interfaces desarrolladas con Swing.
- **Controllers:** gestionan validaciones y comunicación entre vista y datos.
- **DAO:** concentra las operaciones SQL y acceso a la base de datos.
- **DatabaseConnection:** administra las conexiones JDBC mediante Singleton.

### Programación Orientada a Objetos

El proyecto utiliza distintos conceptos de POO:

- **Herencia:** `Usuario` y `Estudiante` heredan de la clase abstracta `Persona`.
- **Abstracción:** se utilizan clases abstractas como `Persona`, `CrudController<T>` y `PanelModulo`.
- **Polimorfismo:** las clases derivadas implementan comportamientos comunes definidos por sus clases base.
- **Interfaces:** `CrudDAO<T>` define las operaciones CRUD comunes utilizadas por distintos DAO.

### DAO

Las operaciones SQL se encuentran separadas de la interfaz gráfica mediante clases DAO.

`CategoriaDAO`, `EstudianteDAO` y `LibroDAO` implementan la interfaz genérica `CrudDAO<T>` con las operaciones:

- crear
- listar
- buscar por ID
- actualizar
- eliminar

`UsuarioDAO` se utiliza para autenticación y `PrestamoDAO` concentra las operaciones relacionadas con préstamos, devoluciones y reportes.

### Singleton

La clase `DatabaseConnection` implementa el patrón Singleton y centraliza la creación de conexiones JDBC hacia MySQL mediante `DriverManager.getConnection()`.

### Concurrencia

Las operaciones contra la base de datos se ejecutan fuera del hilo principal de Swing utilizando `SwingWorker`, evitando que la interfaz se congele.

Además, el módulo `concurrencia/Tareas` utiliza `ExecutorService` para ejecutar en paralelo consultas independientes. Por ejemplo, al preparar un préstamo se pueden cargar simultáneamente los estudiantes y los libros disponibles.

Los préstamos también utilizan transacciones JDBC para mantener la consistencia entre el registro del préstamo y la actualización del stock.

## 🗄️ Base de datos

La base de datos se llama `biblioteca` y utiliza las tablas entregadas para la evaluación:

| Tabla | Descripción |
| --- | --- |
| `usuarios` | Usuarios que pueden autenticarse en el sistema |
| `estudiantes` | Información de los estudiantes |
| `categorias` | Categorías de libros |
| `libros` | Catálogo y stock disponible |
| `prestamos` | Registro de préstamos y devoluciones |

Los scripts oficiales se encuentran en:

```text
db/
├── PRY2203_EFT_S9_Script_crea_tablas_biblioteca.sql
└── PRY2203_EFT_S9_Script_poblado_tablas_biblioteca.sql
```

Deben ejecutarse en ese mismo orden.

## 📁 Estructura principal

```text
src/main/java/cl/duocuc/
├── concurrencia/
├── conexion/
├── controllers/
├── dao/
├── exception/
├── main/
├── model/
└── view/
```

### Packages

- `concurrencia`: tareas ejecutadas mediante hilos.
- `conexion`: conexión Singleton a MySQL.
- `controllers`: lógica y validaciones de la aplicación.
- `dao`: acceso y operaciones sobre la base de datos.
- `exception`: excepciones de persistencia.
- `main`: punto de entrada de la aplicación.
- `model`: entidades del sistema.
- `view`: interfaz gráfica Swing.

## ▶️ Ejecución

### 1. Crear y poblar la base de datos

Ejecutar primero:

```text
PRY2203_EFT_S9_Script_crea_tablas_biblioteca.sql
```

Luego:

```text
PRY2203_EFT_S9_Script_poblado_tablas_biblioteca.sql
```

### 2. Configurar la conexión

Revisar los datos de conexión en:

```text
src/main/java/cl/duocuc/conexion/DatabaseConnection.java
```

La aplicación utiliza la base de datos:

```text
biblioteca
```

### 3. Ejecutar la aplicación

Desde IntelliJ IDEA ejecutar:

```text
cl.duocuc.main.Main
```

## 🔑 Credenciales de prueba

Las credenciales son cargadas por el script oficial de poblado.

| Rol | Correo | Contraseña |
| --- | --- | --- |
| Bibliotecario | `antonia@correo.cl` | `clave123` |
| Estudiante | `carlos@correo.cl` | `clave123` |

El bibliotecario tiene acceso a los módulos de administración. El estudiante puede consultar el catálogo y visualizar sus préstamos asociados.

## 👤 Autor del proyecto

- **Nombre completo:** Nicolás Masnovo
- **Carrera:** Analista Programador Computacional
- **Sede:** Sede Online
- **Asignatura:** Programación Orientada a Objetos 2
- **Docente:** Pablo Andrés Vilches Valenzuela

---

© Duoc UC | Escuela de Informática y Telecomunicaciones
