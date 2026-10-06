package cl.bancoxyz.batch.comun;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;

/** Exporta el resultado de una consulta SQL a un archivo CSV (con encabezado). */
public final class ReporteCsv {

    private ReporteCsv() {
    }

    public static Path exportar(JdbcTemplate jdbc, Path destino, String sql, Object... parametros) throws IOException {
        Files.createDirectories(destino.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            boolean[] encabezado = {false};
            RowCallbackHandler fila = rs -> {
                try {
                    ResultSetMetaData md = rs.getMetaData();
                    int columnas = md.getColumnCount();
                    if (!encabezado[0]) {
                        StringBuilder h = new StringBuilder();
                        for (int i = 1; i <= columnas; i++) {
                            h.append(i > 1 ? "," : "").append(md.getColumnLabel(i).toLowerCase());
                        }
                        out.write(h.toString());
                        out.newLine();
                        encabezado[0] = true;
                    }
                    StringBuilder linea = new StringBuilder();
                    for (int i = 1; i <= columnas; i++) {
                        Object v = rs.getObject(i);
                        String texto = v == null ? "" : v.toString();
                        if (texto.contains(",") || texto.contains("\"")) {
                            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
                        }
                        linea.append(i > 1 ? "," : "").append(texto);
                    }
                    out.write(linea.toString());
                    out.newLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
            jdbc.query(sql, fila, parametros);
        }
        return destino;
    }
}
