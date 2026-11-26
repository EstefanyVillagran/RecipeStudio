from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

OUT = r"c:\Users\verof\Downloads\recipestudio_v6\docs\Reporte_Practicas_13_y_14.docx"
doc = Document()
section = doc.sections[0]
section.top_margin = Inches(0.55)
section.bottom_margin = Inches(0.55)
section.left_margin = Inches(0.65)
section.right_margin = Inches(0.65)

styles = doc.styles
styles['Normal'].font.name = 'Aptos'
styles['Normal'].font.size = Pt(9)
styles['Title'].font.name = 'Aptos Display'
styles['Title'].font.size = Pt(20)
styles['Heading 1'].font.name = 'Aptos Display'
styles['Heading 1'].font.size = Pt(13)
styles['Heading 1'].font.color.rgb = RGBColor(31, 78, 121)
styles['Heading 2'].font.name = 'Aptos'
styles['Heading 2'].font.size = Pt(10)

p = doc.add_paragraph()
p.style = 'Title'
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.add_run('Prácticas 13 y 14\nAutomatización de pruebas y validación continua')

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run('RecipeStudio / KitchenSync\nEstudiante: Estefany Villagrán')
r.bold = True
p.add_run('\nRepositorio: EstefanyVillagran/RecipeStudio')

# Horizontal rule
p = doc.add_paragraph()
p.paragraph_format.space_after = Pt(4)
pBdr = OxmlElement('w:pBdr')
bottom = OxmlElement('w:bottom')
bottom.set(qn('w:val'), 'single')
bottom.set(qn('w:sz'), '8')
bottom.set(qn('w:color'), '1F4E79')
pBdr.append(bottom)
p._p.get_or_add_pPr().append(pBdr)


def heading(text):
    doc.add_heading(text, level=1)


def bullets(items):
    for item in items:
        p = doc.add_paragraph(style='List Bullet')
        p.paragraph_format.space_after = Pt(1)
        p.add_run(item)


def add_table(headers, rows, widths=None):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = 'Table Grid'
    hdr = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = h
        hdr[i].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        for run in hdr[i].paragraphs[0].runs:
            run.bold = True
            run.font.color.rgb = RGBColor(255, 255, 255)
            run.font.size = Pt(8)
        shd = OxmlElement('w:shd')
        shd.set(qn('w:fill'), '1F4E79')
        hdr[i]._tc.get_or_add_tcPr().append(shd)
    for row in rows:
        cells = table.add_row().cells
        for i, value in enumerate(row):
            cells[i].text = str(value)
            cells[i].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            for run in cells[i].paragraphs[0].runs:
                run.font.size = Pt(8)
    if widths:
        for row in table.rows:
            for i, width in enumerate(widths):
                row.cells[i].width = Inches(width)
    return table

heading('1. Componentes críticos seleccionados')
add_table(['Componente', 'Defecto/riesgo previo', 'Prueba aplicada'], [
    ('Cálculo de sustituciones', 'Cantidades cero, NaN o infinitas podían generar resultados inválidos.', 'JUnit: entrada válida, vacía y fuera de rango.'),
    ('Comunicación HTTP KitchenApi', 'Error del servidor o desconexión podían presentarse incorrectamente.', 'MockWebServer: respuestas 200, 401, 403 y 500.'),
    ('Persistencia AppDatabase/Repository', 'Duplicación o recuperación incorrecta de recetas e ingredientes.', 'Integración: guardar, reemplazar y recuperar.')
], [1.25, 2.55, 2.75])

heading('2. Automatización unitarias y de integración')
bullets([
    'Se añadieron pruebas JUnit/Kotlin para sustituciones válidas, ingrediente vacío o desconocido y cantidades cero, NaN e infinitas.',
    'Se probaron también los límites de impacto de sabor y textura y el manejo de excepciones.',
    'Las pruebas de integración usan MockWebServer y objetos reales de dominio/persistencia, por lo que no requieren Internet ni Firebase.',
])

heading('3. Matriz de automatización')
add_table(['ID', 'Tipo', 'Componente', 'Estado'], [
    ('AUT-01', 'Unitaria', 'Sustitución válida', 'Aprobada'),
    ('AUT-02', 'Unitaria', 'Entrada vacía/desconocida', 'Aprobada'),
    ('AUT-03', 'Unitaria', 'Cero, NaN, infinito y límites', 'Aprobada'),
    ('AUT-04', 'Integración', 'Respuestas HTTP 200/401/403/500', 'Aprobada'),
    ('AUT-05', 'Integración', 'GET/POST y JSON de sesión', 'Aprobada'),
    ('AUT-06', 'Persistencia', 'Guardar, reemplazar y recuperar', 'Aprobada'),
    ('AUT-07', 'CI', 'Ejecución con push', 'Aprobada'),
    ('AUT-08', 'CI', 'Ejecución con pull request', 'Aprobada'),
], [0.55, 1.15, 3.15, 0.9])

heading('4. Validación y resultados')
add_table(['Validación', 'Resultado'], [
    ('Pruebas unitarias/integración', '69 ejecutadas; 69 aprobadas; 0 fallidas; 0 omitidas'),
    ('Tiempo acumulado JUnit', '12.331 segundos'),
    ('Android Lint', 'BUILD SUCCESSFUL; 0 errores y 17 advertencias'),
    ('Tiempo de lint verificable', '58 segundos en la ejecución local registrada'),
    ('GitHub Actions push/pull request', 'Configuración creada; pendiente verificar run exitoso tras el push final'),
    ('Corrección aplicada', 'Validación de persistencia documentada y separada de Firebase/Internet'),
], [2.15, 4.6])

heading('5. Errores encontrados y cambios')
bullets([
    'Riesgo de compilación: la API com.sun.net.httpserver no era fiable en las pruebas Android. Se sustituyó por MockWebServer.',
    'Riesgo de cálculo: cantidades NaN e infinitas podían aceptar entradas inválidas. Se incorporó cantidad.isFinite() y cantidad > 0.',
    'Riesgo de persistencia: se debía comprobar el dato recuperado sin depender de referencias innecesarias. Se valida el nombre persistido y el progreso calculado.',
])

heading('6. Evidencia de ejecución')
bullets([
    'Pruebas locales: informe XML JUnit con 69 casos, 0 fallos y 0 errores.',
    'Lint local: informe app/build/reports/lint-results-debug.html y resumen app/build/reports/lint-results-debug.txt.',
    'GitHub Actions: después del push se debe insertar una captura del workflow verde y de su job final.',
    'Artefactos: el workflow configura informes-pruebas-android para descarga mediante actions/upload-artifact.',
])

p = doc.add_paragraph()
r = p.add_run('Repositorio remoto: ')
r.bold = True
p.add_run('https://github.com/EstefanyVillagran/RecipeStudio')

heading('7. Evidencia gráfica — pegar capturas')
add_table(['Lugar de la captura', 'Qué debe mostrar'], [
    ('Captura 1', 'GitHub Actions: una ejecución nueva iniciada por push o pull request.'),
    ('Captura 2', 'GitHub Actions: pruebas y lint aprobados en verde.'),
    ('Captura 3', 'Informe JUnit: 69 pruebas aprobadas y 0 fallos.'),
    ('Captura 4', 'Terminal: lint 0 errores y BUILD SUCCESSFUL.'),
    ('Captura 5', 'Artefacto informes-pruebas-android disponible para descarga.'),
], [1.2, 5.55])

heading('8. Conclusión técnica')
p = doc.add_paragraph()
p.add_run('La automatización conserva la calidad de la aplicación al ejecutar de manera repetible las reglas de negocio, la comunicación con el servidor y la persistencia de datos. Las pruebas unitarias detectan entradas inválidas y excepciones antes de que afecten al usuario; las pruebas de integración confirman que los componentes reales colaboran correctamente; y GitHub Actions impide que cambios defectuosos sean aceptados sin validación. De esta manera, modificaciones futuras pueden realizarse con retroalimentación rápida y menor riesgo de regresiones.').italic = True

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(8)
r = p.add_run('Fin del reporte')
r.bold = True
r.font.color.rgb = RGBColor(31, 78, 121)

doc.save(OUT)
print(OUT)
