# ERP Colegio Little Horizons

**Sistema de Gestión Educativa en consola, hecho con Java 21 y MySQL 8.**

Aplicación de consola para administrar un colegio: estudiantes, calificaciones, asistencia, profesores, clubes y eventos, pagos con facturación electrónica simulada, y préstamo de recursos. Sigue una arquitectura MVC estricta con patrón DAO.

## Tabla de contenido

1. [Título del Proyecto](#1-título-del-proyecto)
2. [Descripción](#2-descripción)
3. [Autores](#3-autores)
4. [Tecnologías Utilizadas](#4-tecnologías-utilizadas)
5. [Requerimientos Previos](#5-requerimientos-previos)
6. [Configuración de la Base de Datos](#6-configuración-de-la-base-de-datos)
7. [Instalación y Ejecución](#7-instalación-y-ejecución)
8. [Estructura del Proyecto](#8-estructura-del-proyecto)
9. [Principios y Patrones de Diseño Aplicados](#9-principios-y-patrones-de-diseño-aplicados)
10. [Funcionalidades Principales](#10-funcionalidades-principales)
11. [Manejo de Errores y Logs](#11-manejo-de-errores-y-logs)
12. [Capturas de Pantalla](#12-capturas-de-pantalla)
13. [Problemas Conocidos y Limitaciones](#13-problemas-conocidos-y-limitaciones)
14. [Futuras Mejoras](#14-futuras-mejoras)
15. [Licencia](#15-licencia)
16. [Contacto](#16-contacto)

---

## 1. Título del Proyecto

**ERP Colegio Little Horizons** (`little-horizons-erp`), versión `1.0-SNAPSHOT`.

Proyecto académico de desarrollo en Java. Repositorio: <https://github.com/gomezacunasneyder-cell/little-horizons-erp>

---

## 2. Descripción

### Propósito

El Colegio Internacional Little Horizons atiende desde preescolar hasta 11° (más de 500 estudiantes y 40 profesores). Este sistema reúne en una sola aplicación, con una sola base de datos MySQL, la información que normalmente vive dispersa en hojas de cálculo y cuadernos: quién estudia en qué grado, qué notas tiene, quién faltó, quién debe pensiones y qué libro o equipo está prestado.

El script `docs/database.sql` trae una **muestra** de datos para demostrar el sistema (40 estudiantes y 33 profesores), no el colegio completo.

### Problemas que resuelve

| Problema | Cómo lo resuelve el sistema |
|---|---|
| Promedios calculados a mano | Promedio por materia, general del estudiante y de un curso, calculados automáticamente. La nota final de una materia se calcula con una política configurable (promedio simple o ponderado). |
| Inasistencias que nadie detecta a tiempo | Reporte de estudiantes con más de 3 ausencias sin excusa en una semana (el límite se configura en `config.txt`). |
| Cartera sin control | Reporte de estudiantes en mora (más de un mes pendiente) con el acudiente a contactar y el total adeudado. |
| Facturas hechas a mano | Factura electrónica simulada en archivo TXT, con consecutivo, IVA, CUFE (hash SHA-384), QR y resolución simulados. |
| Recursos que no se devuelven | Préstamos de recursos a estudiantes y profesores, con devolución y reporte de préstamos vencidos. |
| Datos contradictorios | Reglas de negocio en un solo lugar (controladores) y restricciones en la base de datos (llaves foráneas, `CHECK`, únicos). |

### Funcionalidades principales

- **Estudiantes:** ficha completa, acudientes (con acudiente principal y hermanos), matrícula por año y grado, inscripción en materias.
- **Académica:** calificaciones 0.0 a 5.0 por materia y período (I a IV y Final), boletín, historial, promedios; asistencia diaria por materia y reporte de patrones de inasistencia.
- **Profesores:** ficha, estados, asignación de materias (muchos a muchos), horarios sin conflictos y carga académica.
- **Extracurriculares:** clubes con cupo y profesor encargado; eventos con participantes y posiciones.
- **Financiera:** catálogo de conceptos, valores personalizados (descuentos y becas), pagos con recibo consecutivo, morosos y facturas electrónicas simuladas.
- **Recursos:** catálogo (libros, equipos, dispositivos), préstamos, devoluciones y vencidos.
- **Reportes y estadísticas:** resumen general del colegio y acceso a todos los reportes desde un solo menú.

El detalle de cada módulo está en la sección [10](#10-funcionalidades-principales).

---

## 3. Autores

| | |
|---|---|
| **Nombre completo** | Sneyder Steven Gómez Acuña |
| **Correo electrónico** | gomezacunasneyder@gmail.com |
| **GitHub** | [gomezacunasneyder-cell](https://github.com/gomezacunasneyder-cell) |
| **Rol** | Diseño y desarrollo completo del sistema (modelo, vistas, controladores, base de datos y pruebas) |

Estudiante de ingeniería de software. Proyecto desarrollado como entrega académica.

---

## 4. Tecnologías Utilizadas

| Categoría | Tecnología |
|---|---|
| Lenguaje | Java 21 (usa `record`, `switch` con flechas y `Stream.toList()`) |
| Herramienta de construcción | Apache Maven |
| IDE | IntelliJ IDEA |
| Base de datos | MySQL 8.0 o superior |
| Cliente SQL (opcional) | DBeaver, para cargar el script y revisar los datos |
| Librería externa | `mysql-connector-j` 8.4.0 (driver JDBC), **la única dependencia** |

Todo lo demás es la biblioteca estándar de Java: `java.sql` (JDBC), `java.time`, `java.util.stream`, `java.util.function`, `java.nio.file` y `java.security` (hash del CUFE). No usa frameworks.

---

## 5. Requerimientos Previos

| Software | Versión | Para qué |
|---|---|---|
| **MySQL Server** | **8.0 o superior (se recomienda 8.0.16+)** | Base de datos. Es **obligatorio MySQL 8.0 o superior**: las restricciones `CHECK` del script solo se aplican desde MySQL 8.0.16, y las versiones anteriores las ignoran en silencio. |
| JDK | 21 | Compilar y ejecutar |
| Maven | 3.9 o superior (IntelliJ IDEA lo trae incluido) | Descargar el driver y compilar |
| Git | cualquiera reciente | Clonar el repositorio |
| IntelliJ IDEA | Community o Ultimate | IDE recomendado |

Consola en Windows: el programa imprime en UTF-8 (tildes y marcos). Se ve bien en la ventana *Run* de IntelliJ y en Windows Terminal. Si usa `cmd.exe` y ve caracteres extraños, ejecute antes `chcp 65001`.

---

## 6. Configuración de la Base de Datos

### 6.1. Creación de la base de datos

El archivo [`docs/database.sql`](docs/database.sql) crea todo desde cero: estructura (DDL) y datos de prueba (DML).

> **Advertencia:** la primera sentencia **elimina** la base `erp_little_horizons` si existe y la vuelve a crear. Ejecutarlo borra los datos anteriores.

**Opción A: DBeaver (o cualquier cliente SQL)**

1. Conéctese a su servidor MySQL.
2. Abra `docs/database.sql` (*File → Open File*).
3. Ejecútelo **como script completo** (en DBeaver: `Alt + X`), no sentencia por sentencia.

**Opción B: cliente de línea de comandos `mysql`**

Abra la consola desde la raíz del proyecto y escriba:

```
mysql -u root -p
mysql> SOURCE docs/database.sql;
```

(Use `SOURCE` dentro del cliente; el operador `<` no funciona en PowerShell.)

El script fija la codificación de la conexión (`SET NAMES utf8mb4`), así que las tildes y las eñes se cargan bien sin importar la configuración del cliente.

Al terminar, el script muestra una tabla de verificación. El resultado esperado:

- **26 tablas**, con todas las llaves primarias, foráneas, únicos, índices y `CHECK`: las 22 del anexo del proyecto más 4 nuevas que exige el enunciado (`horarios_asignacion`, `valores_concepto_estudiante`, `consecutivos` y `detalle_factura`).
- Las notas son `DECIMAL(2,1)` y los préstamos están en dos tablas separadas (`prestamos_estudiante` y `prestamos_profesor`), según el anexo del enunciado.
- Datos de prueba coherentes y con integridad referencial:

| Tabla | Filas |
|---|---|
| `estudiantes` | 40 |
| `acudientes` | 44 |
| `profesores` | 33 |
| `materias` | 71 |
| `registros_academicos` | 1.477 |
| `pagos` | 500 |
| `clubes` | 34 |
| `recursos` | 47 |

El encabezado del script lista cada cambio respecto al script original (`[CAMBIO 1]` a `[CAMBIO 12]`) y su justificación.

### 6.2. Configuración de credenciales

Las credenciales **no** están en el repositorio. Cree su propio `config.txt` en la raíz del proyecto (la misma carpeta que `pom.xml`) a partir del ejemplo:

```
copy config.example.txt config.txt        (Windows)
cp config.example.txt config.txt          (Linux / macOS)
```

Edite los tres valores obligatorios:

```
db.url=jdbc:mysql://localhost:3306/erp_little_horizons
db.user=su_usuario
db.password=su_contraseña
```

- Si su usuario de MySQL no tiene contraseña, deje la línea `db.password=` vacía.
- `config.txt` está en `.gitignore`: nunca se sube al repositorio.
- Hay claves **opcionales**, comentadas en `config.example.txt`: política de nota final (`grading.final.strategy`, `grading.final.weights`), límite semanal de ausencias (`attendance.weekly.absence.limit`) y datos de la factura simulada (`billing.*`). Si faltan, el programa usa valores por defecto.
- **Importante:** el programa busca `config.txt` (y escribe `error_log.txt`) en el *directorio de trabajo*. Ejecútelo siempre desde la raíz del proyecto. En IntelliJ ese es el valor por defecto.

### 6.3. Instalación del driver JDBC

No hay que instalar nada a mano. El driver está declarado en el `pom.xml`:

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
</dependency>
```

Maven lo descarga la primera vez que se abre o recarga el proyecto. Desde JDBC 4 el driver se registra solo, por lo que no hace falta `Class.forName(...)`. Si el programa dice *«No se encontró el driver de MySQL»*, recargue Maven (en IntelliJ: panel *Maven* → icono *Reload All Maven Projects*).

---

## 7. Instalación y Ejecución

### 7.1. Clonar el repositorio

```
git clone https://github.com/gomezacunasneyder-cell/little-horizons-erp.git
cd little-horizons-erp
```

### 7.2. Abrir el proyecto en el IDE

1. En IntelliJ IDEA: *File → Open* y seleccione el archivo `pom.xml` (opción *Open as Project*).
2. Espere a que Maven termine de importar el proyecto y descargue el driver.
3. Verifique el SDK: *File → Project Structure → Project → SDK* debe ser Java 21.
4. Cree `config.txt` como se explica en 6.2 y cargue la base de datos como se explica en 6.1.

### 7.3. Compilar

- En IntelliJ: *Build → Build Project* (`Ctrl + F9`).
- Con Maven: `mvn clean compile`

### 7.4. Ejecutar

- En IntelliJ: abra `src/main/java/com/littlehorizons/erp/Main.java` y pulse el botón verde junto a `main` (*Run 'Main.main()'*).
- Desde la terminal, después de compilar:

```
mvn dependency:copy-dependencies -DoutputDirectory=target/lib
java -cp "target/classes;target/lib/*" com.littlehorizons.erp.Main
```

(En Linux y macOS el separador del classpath es `:` en lugar de `;`.)

Si todo está bien, el programa muestra el banner, confirma la conexión (`[OK] Conexión con la base de datos establecida.`) y abre el menú principal. Si algo falla, muestra un mensaje claro y deja el detalle técnico en `error_log.txt`.

**Cómo moverse por los menús**

- Elija una opción escribiendo su número.
- Escriba `cancelar` en cualquier pregunta para abortar la operación y volver un nivel.
- Escriba `inicio` en cualquier pregunta o menú para ir directo al menú principal.
- Muchas preguntas aceptan Enter para usar el valor por defecto (por ejemplo, la fecha de hoy).
- Los datos mal escritos (una letra donde va un número, una fecha imposible) se vuelven a preguntar con un mensaje claro; el programa no se cae.

### 7.5. Verificación con las clases de prueba

En `src/test/java/com/littlehorizons/erp/` hay 13 clases de verificación manual (`ConnectionCheck`, `StudentModuleCheck`, `TeacherModuleCheck`, `GradeModuleCheck`, `AttendanceModuleCheck`, `ClubModuleCheck`, `EventModuleCheck`, `ConceptModuleCheck`, `PaymentModuleCheck`, `InvoiceModuleCheck`, `ResourceModuleCheck`, `LoanModuleCheck` y `ReportsModuleCheck`). Cada una tiene su propio `main`: ejecútelas desde IntelliJ con la base de datos cargada y `config.txt` en su sitio. Imprimen `PASS` o `FAIL` por comprobación, terminan con `ALL CHECKS PASSED` y devuelven código de salida 1 si algo falla. Las que escriben datos usan códigos de prueba propios (por ejemplo `ZZP-`) y los borran al terminar: la base queda con las mismas filas que tenía y los consecutivos de recibos y facturas no avanzan.

---

## 8. Estructura del Proyecto

### Arquitectura MVC

El proyecto sigue MVC de forma estricta, con una capa DAO dentro del modelo:

```
 Usuario ── teclado ──▶  VISTA  ──▶  CONTROLADOR  ──▶  DAO (interfaz)  ──▶  Jdbc*Dao  ──▶  MySQL
                         ConsoleInput    reglas de        model.dao          model.dao.jdbc
                         ConsoleOutput   negocio, Result<T>
 Usuario ◀── pantalla ──  VISTA  ◀──  Result<T>  ◀──  CONTROLADOR  ◀──  entidades y listas
```

- **Modelo** (`model`): entidades del dominio, enumeraciones y acceso a datos. Es la única capa que conoce JDBC.
- **Vista** (`view`): menús, formularios e impresión en consola. No contiene reglas de negocio ni SQL.
- **Controlador** (`controller`): reglas de negocio y cálculos. Recibe datos de la vista, usa los DAO y devuelve un `Result<T>` (éxito con dato y mensaje, o error con mensaje). Nunca lanza excepciones hacia la vista.

Reglas que se cumplen en todo el código (verificables con una búsqueda de texto):

| Regla | Dónde se permite |
|---|---|
| `java.sql` | Solo en `model/dao/jdbc` |
| `Scanner` (entrada) | Solo en `view/ConsoleInput` |
| `System.out` (salida) | Solo en `view` y `Main` |
| Un controlador no importa nada de `view` ni de `model.dao.jdbc` | Los controladores dependen de interfaces DAO |

### Árbol del proyecto

```
little-horizons-erp/
├── docs/
│   └── database.sql                 DDL + DML (26 tablas y datos de prueba)
├── src/
│   ├── main/java/com/littlehorizons/erp/
│   │   ├── Main.java                Punto de entrada: crea los objetos y los conecta
│   │   ├── model/
│   │   │   ├── entity/              Entidades del dominio (Student, Teacher, Loan...)
│   │   │   ├── enums/               Enumeraciones con atributos (reflejan los ENUM de MySQL)
│   │   │   └── dao/                 Interfaces DAO + DaoFactory
│   │   │       └── jdbc/            Implementaciones JDBC (Jdbc*Dao), conexión, transacciones
│   │   ├── controller/              Reglas de negocio (un controlador por caso de uso)
│   │   │   └── attendance, club, event, finance, grading, loan, report, resource
│   │   │                            Cálculos puros y registros de resultado
│   │   ├── view/                    Menús, formularios, impresores de tablas, entrada y salida
│   │   ├── exception/               Excepciones propias
│   │   └── util/                    Configuración, registro de errores y utilidades
│   └── test/java/com/littlehorizons/erp/
│       └── *Check.java              13 clases de verificación (sección 7.5)
├── config.example.txt               Plantilla de configuración (copiar a config.txt)
├── pom.xml                          Proyecto Maven (Java 21 + driver MySQL)
├── LICENSE
└── README.md
```

No se versionan, y se generan al ejecutar: `config.txt` (sus credenciales), `error_log.txt` (registro de errores), `invoices/` (facturas TXT) y `target/` (compilación).

### Paquetes principales

| Paquete | Contenido |
|---|---|
| `model.entity` | Entidades con sus validaciones propias: `Person` (abstracta) y sus hijas `Student`, `Teacher`, `Guardian`; además `Enrollment`, `AcademicRecord`, `AttendanceRecord`, `Club`, `SchoolEvent`, `Payment`, `Invoice`, `Resource`, `Loan`, `Borrower`, etc. |
| `model.enums` | Enumeraciones que implementan `DbEnum`: cada constante guarda la etiqueta exacta de la base de datos y el nombre para mostrar. Evitan cadenas «mágicas». |
| `model.dao` | Una interfaz por conjunto de datos (`StudentDao`, `LoanDao`...), `DaoFactory` y `ConnectionVerifier`. |
| `model.dao.jdbc` | Implementaciones con `PreparedStatement`: `DatabaseConnection`, `Transactions`, `RowMapper`, `SqlErrorTranslator`, `JdbcDaoFactory` y los `Jdbc*Dao`. |
| `controller` | Un controlador por caso de uso (`StudentController`, `PaymentController`, `LoanController`...). `BaseController` concentra el manejo de errores y `Result<T>` es el objeto de respuesta. |
| `controller.*` (subpaquetes) | Cálculos puros con Streams y registros de resultado (`LoanAnalyzer`, `PaymentAnalyzer`, `OverviewAnalyzer`, `InvoiceCalculator`, estrategias de nota final...). Sin base de datos ni consola, fáciles de probar. |
| `view` | `MainMenuView`, un menú por módulo, formularios (`StudentForm`...), impresores (`LoanPrinter`...), `ConsoleInput` y `ConsoleOutput`. |
| `exception` | `ValidationException`, `DaoException`, `ConfigurationException` y las de navegación. |
| `util` | `AppConfig`, `ErrorLogger`, `Money`, `EnumCounts`. |

---

## 9. Principios y Patrones de Diseño Aplicados

### 9.1. Principios SOLID

**S: Responsabilidad única.** Cada clase tiene una sola razón para cambiar.

- `ConsoleInput` solo lee y valida lo que escribe el usuario; `ConsoleOutput` solo imprime.
- La factura se reparte en clases pequeñas: `InvoiceCalculator` (importes e IVA), `CufeGenerator` (el hash), `SimulatedQr` (el QR simulado), `InvoiceTextRenderer` (el texto) e `InvoiceFileStore` (guardar el archivo).
- Las reglas viven en los controladores; el SQL, en los DAO; la presentación, en las vistas.

**O: Abierto/cerrado.** Se amplía sin modificar lo que ya funciona.

- La nota final de una materia es una política que puede cambiar. `FinalGradeStrategy` tiene dos implementaciones (`SimpleAverageStrategy` y `WeightedAverageStrategy`) y se elige en `config.txt`. Agregar una tercera no toca el controlador de notas.
- Agregar un módulo nuevo al menú principal es crear una clase `MenuView` y añadir una línea en `Main`:

```java
new MainMenuView(input, output, startup,
        Map.of(1, studentModule, 2, academicModule, 3, teacherModule, 4, extracurricularModule,
               5, financeModule, 6, resourceModule, 7, reportsModule)).start();
```

**L: Sustitución de Liskov.** Los subtipos pueden usarse donde se espera su tipo base.

- `Student`, `Teacher` y `Guardian` extienden `Person` y respetan su contrato (`getFullName()`, validaciones de documento, teléfono y correo).
- `MainMenuView` guarda un `Map<Integer, MenuView>` y llama a `show()` sin saber qué módulo es: cualquier `MenuView` sirve.
- Cada `Jdbc*Dao` puede reemplazar a su interfaz sin que el controlador lo note.

**I: Segregación de interfaces.** Interfaces pequeñas y específicas, no una gigante.

- Una interfaz DAO por conjunto de datos (`StudentDao`, `LoanDao`, `ClubDao`...), de modo que cada controlador solo conoce los métodos que usa.
- `MenuView` tiene un solo método (`show()`), `ConnectionVerifier` también (`verify()`) y `DbEnum` solo dos.

**D: Inversión de dependencias.** Lo concreto depende de abstracciones.

- Los controladores reciben interfaces por el constructor y nunca hacen `new JdbcStudentDao()`:

```java
public StudentController(StudentDao studentDao, ErrorLogger logger) { ... }
public StartupController(ConnectionVerifier database, ErrorLogger logger) { ... }
public LoanController(LoanDao loanDao, ResourceDao resourceDao, ErrorLogger logger, Supplier<LocalDate> today) { ... }
```

- Hasta la fecha de «hoy» se inyecta (`Supplier<LocalDate>`), así que las pruebas pueden fijar el reloj.
- `Main` es la «raíz de composición»: el único lugar que crea los objetos concretos y los conecta (inyección de dependencias manual, sin frameworks).

### 9.2. Patrones de Diseño

| Patrón | Dónde | Justificación |
|---|---|---|
| **Singleton** | `AppConfig`, `ErrorLogger`, `DatabaseConnection` | Hay una sola configuración, un solo archivo de log y un solo punto que conoce las credenciales. `DatabaseConnection` no guarda una conexión compartida: entrega una nueva en cada llamada para que cada quien la cierre con *try-with-resources*. |
| **DAO** | `model.dao` (interfaces) y `model.dao.jdbc` | Aísla todo el SQL. Los controladores no saben que hay una base de datos relacional detrás. |
| **Factory** | `DaoFactory` y `JdbcDaoFactory` | Los controladores piden sus DAO a la fábrica. Cambiar la tecnología de datos solo cambia la fábrica. |
| **Builder** | `Student.builder()...build()` | `Student` tiene muchos datos opcionales; el *builder* evita constructores gigantes y valida al construir. |
| **Template Method** | `AbstractMenuView` | El ciclo «mostrar menú, leer opción, ejecutar, volver» es igual en todos los módulos y se escribe una sola vez. Cada menú solo define `title()`, `options()` y `handle(int)`. |
| **Strategy** | `FinalGradeStrategy` | La política de nota final cambia según el colegio; cada política es una clase intercambiable. |
| **Inyección de dependencias** | `Main` | Desacopla las capas y permite probar con datos o relojes de prueba. |
| **Objeto de valor / registro** | `Result<T>`, `Borrower`, `SchoolOverview`, `OverdueReport`... | Datos inmutables que viajan entre capas sin lógica de acceso a datos. |

### 9.3. Lambdas y Streams (complemento)

- **Lambdas como pieza de diseño:** `ConsoleInput.read(prompt, parser)` recibe el *parser* como lambda y todas las demás lecturas (`readInt`, `readDate`, `readMoney`...) son solo parsers distintos. `Transactions.run(database, connection -> { ... })` hace el `commit` y el `rollback` una sola vez para todos los DAO. `BaseController.execute(etiqueta, () -> ...)` convierte excepciones en `Result`.
- **Streams en los cálculos:** agrupar, contar, ordenar y totalizar. Por ejemplo, las matrículas por grado, en orden de grado:

```java
return enrollments.stream()
        .collect(Collectors.groupingBy(Enrollment::getGradeLevel,
                () -> new TreeMap<>(Comparator.comparingInt(GradeLevel::getLevel)),
                Collectors.counting()))
        .entrySet().stream()
        .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue,
                Long::sum, LinkedHashMap::new));
```

---

## 10. Funcionalidades Principales

Menú principal: **1** Gestión de Estudiantes · **2** Gestión Académica · **3** Gestión de Profesores · **4** Actividades Extracurriculares · **5** Gestión Financiera · **6** Gestión de Recursos · **7** Reportes y Estadísticas · **8** Salir.

### 1. Gestión de Estudiantes

- Registrar, listar, buscar, ver la ficha, editar y retirar estudiantes (tipos de documento TI, CC, CE y Pasaporte; estados Activo, Graduado, Retirado y Suspendido). Retirar cierra sus matrículas e inscripciones activas.
- **Acudientes:** ver, agregar, quitar y editar acudientes, cambiar el acudiente principal y ver hermanos (estudiantes de un mismo acudiente).
- **Matrículas:** matricular por año y grado, ver las de un estudiante, listar un grado en un año y cerrar la matrícula (completar o retirar).
- **Inscripción en materias:** una materia, todas las de su grado, ver y retirar inscripciones.

### 2. Gestión Académica

- **Calificaciones y promedios:** registrar una nota (0.0 a 5.0, un decimal; acepta coma o punto), registrar las de un grupo completo, corregir, calcular la nota final de una materia (promedio simple o ponderado), boletín por año, historial desde el ingreso y promedios de un curso por materia. Solo puede calificar un profesor activo asignado a esa materia, y el estudiante debe estar inscrito en ella.
- **Control de asistencia:** registrar un grupo o un estudiante (Presente, Ausente, Tardanza, Excusa Médica), corregir, consultar un grupo en una fecha, resumen por estudiante y **reporte de patrones de inasistencia** (más de 3 ausencias sin excusa en una semana, configurable). Solo días hábiles.

### 3. Gestión de Profesores

- Registrar, listar, buscar, ver la ficha, editar y cambiar el estado (Activo, Inactivo, Licencia).
- **Asignaciones y horarios:** asignar materias (relación muchos a muchos), agregar bloques de horario **sin conflictos** (un profesor no puede estar en dos lugares a la vez), ver el horario semanal de un profesor o de un grupo y la carga académica.

### 4. Actividades Extracurriculares

- **Clubes:** catálogo con cupos y disponibilidad, crear, editar, cerrar o reabrir; inscribir y retirar estudiantes (con fecha y estado Activo/Retirado); ver miembros y los clubes de un estudiante. Respeta el cupo máximo y no permite dos inscripciones activas del mismo estudiante en un club.
- **Eventos:** calendario, registrar, editar, cancelar o reactivar; participantes y posiciones (1er, 2do y 3er lugar o Participante) en eventos de competencia; resultados y eventos de un estudiante. Tipos: Académico, Cultural, Deportivo y Social.

### 5. Gestión Financiera

- **Conceptos de pago:** catálogo (matrícula, pensión, uniformes, seguro, plataforma virtual...), alta y edición, activar o desactivar; **valores personalizados** por estudiante (descuento por hermanos, beca) con su motivo.
- **Pagos:** registrar (Efectivo, Transferencia o Tarjeta) con número de recibo consecutivo que entrega el sistema (`REC-000001`), historial por estudiante o por acudiente (todos sus hijos) y **reporte de morosos** (más de un mes pendiente, con el acudiente a contactar).
- **Facturas electrónicas (simuladas):** se emiten para pagos ya recibidos. Incluyen datos del colegio S.A.S., cliente, consecutivo (`FE-000001`), detalle, IVA, **CUFE** (hash SHA-384), QR y resolución simulados. Se guardan como archivo **TXT** con nombre único (`factura_000001.txt`) en la carpeta `invoices/` y pueden regenerarse en cualquier momento.

### 6. Gestión de Recursos

- **Catálogo:** inventario, búsqueda, filtros por categoría (Libro, Equipo de Laboratorio, Equipo Deportivo, Dispositivo Tecnológico, Material Didáctico) y estado (Disponible, Prestado, En Mantenimiento, Dado de Baja); registrar, editar, cambiar el estado y eliminar (solo si nunca se ha prestado).
- **Préstamos y devoluciones:** prestar a un estudiante o a un profesor activo (tablas separadas, como pide el anexo), con devolución programada (7 días por defecto, máximo 90); registrar la devolución (con confirmación); ver pendientes, **reporte de préstamos vencidos** (calculado por fecha, con días de retraso y contacto) e historial por recurso o por persona. Un recurso no puede prestarse dos veces.

### 7. Reportes y Estadísticas

- **Resumen general del colegio:** estudiantes y profesores por estado, matrículas por grado, clubes, eventos, recursos, préstamos y mora, en una sola pantalla.
- Acceso directo a los reportes de cada módulo: promedios de un curso, patrones de inasistencia, estudiantes en mora, ocupación de clubes, calendario de eventos, inventario con filtros y préstamos vencidos.

### Navegación

Entrada validada con reintento, `cancelar` para retroceder un nivel e `inicio` para volver al menú principal desde cualquier punto.

---

## 11. Manejo de Errores y Logs

### Sistema de logging

La clase `ErrorLogger` (Singleton) escribe en el archivo **`error_log.txt`**, ubicado en la raíz del proyecto (el directorio de trabajo). Se crea solo la primera vez y los registros se agregan al final. No está versionado.

- Cada línea lleva fecha y hora, nivel (`INFO`, `WARN` o `ERROR`) y mensaje; los errores incluyen el *stack trace* completo. Esto es lo que queda en `error_log.txt` cuando se arranca el programa con el servidor de MySQL apagado (entrada real, recortada):

```
2026-11-17 08:12:19 [ERROR] Startup check failed: No se pudo conectar con MySQL. Verifique que el servidor esté en ejecución y que la url de config.txt sea correcta.
com.littlehorizons.erp.exception.DaoException: No se pudo conectar con MySQL. Verifique que el servidor esté en ejecución y que la url de config.txt sea correcta.
	at com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection.getConnection(DatabaseConnection.java:52)
	at com.littlehorizons.erp.model.dao.jdbc.DatabaseConnection.verify(DatabaseConnection.java:59)
	at com.littlehorizons.erp.controller.StartupController.checkDatabase(StartupController.java:24)
	at com.littlehorizons.erp.view.MainMenuView.start(MainMenuView.java:46)
	at com.littlehorizons.erp.Main.main(Main.java:164)
Caused by: java.sql.SQLException: ... (la causa original que reporta el driver JDBC)
```

  En pantalla, en cambio, el usuario solo ve el mensaje claro (`[ERROR] No se pudo conectar con MySQL...`) y la indicación de revisar `error_log.txt`.
- El método de escritura está sincronizado (dos hilos no mezclan líneas) y no depende de `config.txt` (debe poder registrar que `config.txt` falta). Si no puede escribir el archivo, avisa por `System.err` y sigue: el registro nunca es la causa de una caída.
- La etiqueta de cada entrada (`Startup check failed`, `Retire student`...) está en inglés, como el código; el mensaje que la acompaña es el mismo texto en español que ve el usuario.

### Estrategia de manejo de excepciones

El usuario ve un mensaje claro y entendible; el detalle técnico va al log. Cada tipo de problema tiene su propia excepción:

| Excepción | Tipo | Qué representa | Qué pasa |
|---|---|---|---|
| `ValidationException` | sin comprobar | Dato inválido o regla de negocio incumplida (fecha imposible, cupo lleno, recurso ya prestado) | Se muestra el mensaje; **no** se registra en el log (es un error del usuario, no del sistema) |
| `DaoException` | comprobada | Falla de acceso a datos. Envuelve la `SQLException` original con un mensaje ya traducido | Se registra **una sola vez** (en `BaseController`) con su *stack trace* y se muestra el mensaje |
| `ConfigurationException` | sin comprobar | Falta `config.txt` o una clave obligatoria | Se registra y se explica qué corregir |
| `OperationCancelledException`, `InputClosedException`, `MainMenuRequestedException` | sin comprobar | Navegación: `cancelar`, entrada cerrada o `inicio` | Se atrapan en los menús; no son errores |

Cómo se aplica en la práctica:

- **`DaoException` es comprobada a propósito:** el compilador obliga a cada controlador a decidir qué hacer cuando la base de datos falla.
- **`BaseController.execute(...)`:** envuelve cada operación, convierte las excepciones esperadas en `Result.fail(mensaje)` y las registra una sola vez. La vista nunca ve una excepción.
- **`SqlErrorTranslator`** convierte el código de error de MySQL en una frase comprensible:

| Código MySQL | Mensaje al usuario |
|---|---|
| 1045 | Usuario o contraseña de MySQL incorrectos. Revise config.txt. |
| 1049 | La base de datos no existe. Ejecute docs/database.sql primero. |
| 1062 | Ya existe un registro con ese valor único (documento, código o similar). |
| 1406 | Uno de los datos es demasiado largo para el campo. |
| 1451 | No se puede eliminar: otros registros dependen de este. |
| 1452 | El registro relacionado no existe (por ejemplo, un id inválido). |
| 3819 | Un valor no cumple las reglas de la base de datos (rango, monto o fecha). |
| Servidor apagado o `url` errónea | No se pudo conectar con MySQL. Verifique que el servidor esté en ejecución y que la url de config.txt sea correcta. |
| Cualquier otro | Error inesperado de base de datos. Consulte error_log.txt. |

- **`PreparedStatement` siempre**, nunca concatenación de texto en el SQL: no hay inyección SQL.
- **`try-with-resources`** en todo acceso a `Connection`, `PreparedStatement` y `ResultSet`: nada queda abierto aunque algo falle.
- **Transacciones:** las operaciones de varios pasos (por ejemplo, prestar un recurso y cambiar su estado) usan `Transactions.run(...)`: se guardan completas o se deshacen completas (*rollback*).
- **Entrada validada con reintento:** `ConsoleInput` repite la pregunta hasta recibir un dato válido; un error de tipeo no detiene el programa.

---

## 12. Capturas de Pantalla

Salida real del programa con los datos de `docs/database.sql`.

**Arranque y menú principal**

```
╔══════════════════════════════════════════════╗
║   ERP Colegio Little Horizons                ║
║   Sistema de Gestión Educativa               ║
╚══════════════════════════════════════════════╝

[OK] Conexión con la base de datos establecida.
[INFO] En cualquier pregunta escriba «cancelar» para volver atrás o «inicio» para ir al menú principal.

1. Gestión de Estudiantes
2. Gestión Académica
3. Gestión de Profesores
4. Actividades Extracurriculares
5. Gestión Financiera
6. Gestión de Recursos
7. Reportes y Estadísticas
8. Salir

Seleccione una opción:
```

**Reportes y Estadísticas → Resumen general del colegio**

```
[INFO] Resumen general del colegio al 2026-11-17 (año académico 2026)
+-------------+------------------------------------+-------------------------------------------------------------------------+
| Módulo      | Indicador                          | Valor                                                                   |
+-------------+------------------------------------+-------------------------------------------------------------------------+
| Estudiantes | Registrados                        | 40                                                                      |
|             | Por estado                         | Activo 37  |  Graduado 2  |  Retirado 1  |  Suspendido 0                |
|             | Matrículas activas 2026            | 37                                                                      |
| Profesores  | Registrados                        | 33                                                                      |
|             | Por estado                         | Activo 29  |  Inactivo 2  |  En licencia 2                              |
| Clubes      | Activos / total                    | 32 / 34                                                                 |
|             | Inscripciones activas              | 148                                                                     |
|             | Clubes llenos / cupos libres       | 2 / 410                                                                 |
| Eventos     | Próximos / realizados / cancelados | 3 / 21 / 1                                                              |
|             | Participaciones                    | 215                                                                     |
| Recursos    | Registrados                        | 47                                                                      |
|             | Por estado                         | Disponible 30  |  Prestado 10  |  En Mantenimiento 4  |  Dado de Baja 3 |
| Préstamos   | Pendientes / vencidos              | 10 / 5                                                                  |
| Finanzas    | Estudiantes en mora                | 6                                                                       |
|             | Total pendiente                    | $11.400.000                                                             |
+-------------+------------------------------------+-------------------------------------------------------------------------+
```

**Gestión de Recursos → Reporte de préstamos vencidos**

```
+---------+---------------------------------------+-------------------------------+------------+---------------------------------------+------------+-----------------+
| Código  | Recurso                               | Solicitante                   | Tipo       | Contacto                              | Vencía     | Días de retraso |
+---------+---------------------------------------+-------------------------------+------------+---------------------------------------+------------+-----------------+
| LIB-001 | Cien años de soledad                  | María Fernanda Pérez Torres   | Estudiante | -                                     | 2026-10-12 | 36              |
| DEP-006 | Raquetas de tenis de mesa             | Rubén Hernández López         | Profesor   | ruben.hernandez@littlehorizons.edu.co | 2026-10-14 | 34              |
| TEC-006 | Calculadora científica Casio fx-991LA | David Arango Pardo            | Estudiante | -                                     | 2026-11-02 | 15              |
| TEC-001 | Tablet Samsung Galaxy Tab A9          | Daniel Alejandro Ruiz Herrera | Estudiante | -                                     | 2026-11-09 | 8               |
| LAB-002 | Microscopio óptico binocular          | Sergio Eduardo Duque Mejía    | Profesor   | sergio.duque@littlehorizons.edu.co    | 2026-11-13 | 4               |
+---------+---------------------------------------+-------------------------------+------------+---------------------------------------+------------+-----------------+
[INFO] Préstamos vencidos al 2026-11-17: 5. Mayor retraso: 36 día(s).
[INFO] Por solicitante: Estudiante 3  |  Profesor 2
[INFO] Por categoría:   Libro 1  |  Equipo de Laboratorio 1  |  Equipo Deportivo 1  |  Dispositivo Tecnológico 2  |  Material Didáctico 0
```

Los días de retraso dependen de la fecha en que se ejecute el programa (los datos de ejemplo están calculados alrededor de noviembre de 2026).

**Gestión Financiera → Facturas electrónicas → archivo `invoices/factura_000001.txt`** (fragmento; `[...]` marca lo omitido)

```
==============================================================================
                 Colegio Internacional Little Horizons S.A.S.
                              NIT 901.234.567-8
                       Carrera 15 # 85-30, Bogotá D.C.
          Tel. (601) 745 2100  |  facturacion@littlehorizons.edu.co
==============================================================================
                         FACTURA ELECTRÓNICA DE VENTA
                                No. FE-000001
               (DOCUMENTO SIMULADO - SIN VALIDEZ ANTE LA DIAN)
==============================================================================
[...]
DETALLE
Descripción                              Cant.  Valor unitario        Subtotal
------------------------------------------------------------------------------
Pensión mensual - cuota de junio 2026        1        $650.000        $650.000
  Recibo REC-000501 | IVA 0%
------------------------------------------------------------------------------
                                        Subtotal (sin IVA):           $650.000
                                                    IVA 0%:                 $0
                                       TOTAL A PAGAR (COP):           $650.000
==============================================================================
ELEMENTOS DE CUMPLIMIENTO DIAN (SIMULADOS)
CUFE (SHA-384):
  e956ec06c2124fa80a32a0c3bef3a53833966b31f9a4bc4b
  33158a01624234edbe2894775ad63b55b39de6d044a7e5d0
Ambiente         : 2 - Habilitación (pruebas)
[...]
CÓDIGO QR (SIMULADO) - contenido que llevaría:
  NumFac: FE-000001
[...]
==============================================================================
      Documento académico: no es una factura real ni se envía a la DIAN.
==============================================================================
```

El archivo completo también incluye los datos del cliente y del estudiante, la resolución simulada y un dibujo del QR en bloques de texto.

---

## 13. Problemas Conocidos y Limitaciones

- **Solo consola y un solo usuario:** no hay inicio de sesión ni roles. Quien ejecuta el programa tiene acceso a todos los módulos.
- **Facturación simulada:** el CUFE, el QR y la resolución son ficticios y **no** tienen validez ante la DIAN. El QR es un dibujo en texto, no se puede escanear. El formato es TXT (no PDF).
- **Pruebas manuales:** las clases de verificación (sección 7.5) se ejecutan a mano y necesitan la base de datos cargada. No se usa JUnit ni integración continua.
- **MySQL 8.0.16 o superior:** en versiones anteriores los `CHECK` se ignoran y la base pierde protección contra datos inconsistentes.
- **Ejecutar desde la raíz del proyecto:** `config.txt`, `error_log.txt` e `invoices/` se resuelven respecto al directorio de trabajo.
- **Historial de 2025:** las notas de 2025 son historial y no tienen inscripciones ni asignaciones de profesor de ese año asociadas; esas tablas guardan solo el año en curso.
- **Datos de ejemplo con fecha:** los préstamos y pagos de `database.sql` están calculados alrededor de noviembre de 2026; los reportes de vencidos y morosos dependen de la fecha del equipo.
- **Terminal de Windows:** si `cmd.exe` no usa UTF-8 se ven mal las tildes y los marcos (ver sección 5).

---

## 14. Futuras Mejoras

- Autenticación y roles (administrador, profesor, secretaría, acudiente).
- Interfaz web o API REST que reutilice los controladores y el modelo sin cambios.
- Pruebas automáticas con JUnit y una base de datos temporal, ejecutadas en integración continua.
- Facturación electrónica real a través de un proveedor tecnológico autorizado por la DIAN, con PDF.
- Exportar reportes a CSV o Excel.
- Notificaciones por correo o WhatsApp a los acudientes (mora, inasistencias) y a quienes tienen préstamos vencidos.
- Paginación de listados largos y pool de conexiones.
- Mensajes en archivos de recursos para soportar varios idiomas.

---

## 15. Licencia

Este proyecto se distribuye bajo la **Licencia MIT**. Consulte el archivo [`LICENSE`](LICENSE) para el texto completo.

Los datos de prueba (personas, documentos, teléfonos, correos y direcciones) son **ficticios**, creados para demostrar el sistema.

---

## 16. Contacto

**Sneyder Steven Gómez Acuña**

- Correo: gomezacunasneyder@gmail.com
- GitHub: [@gomezacunasneyder-cell](https://github.com/gomezacunasneyder-cell)
- Reporte de problemas y sugerencias: abra un *issue* en el repositorio <https://github.com/gomezacunasneyder-cell/little-horizons-erp/issues>
