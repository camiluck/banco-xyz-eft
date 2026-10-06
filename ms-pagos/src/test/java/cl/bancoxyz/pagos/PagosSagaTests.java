package cl.bancoxyz.pagos;

import cl.bancoxyz.pagos.integracion.CuentasClient;
import cl.bancoxyz.pagos.integracion.CuentasClient.MovimientoCuenta;
import cl.bancoxyz.pagos.integracion.CuentasNoDisponibleException;
import cl.bancoxyz.pagos.repositorio.PagoRepository;
import cl.bancoxyz.pagos.servicio.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica la orquestación (saga) de pagos simulando las respuestas de ms-cuentas. */
@SpringBootTest
@AutoConfigureMockMvc
class PagosSagaTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    PagoService servicio;
    @Autowired
    PagoRepository pagos;
    @MockitoBean
    CuentasClient cuentas;

    private static RequestPostProcessor servicioInterno() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_banco.api"));
    }

    private static MovimientoCuenta ok(long cuentaId, long clienteId) {
        return new MovimientoCuenta(1L, cuentaId, clienteId, "X", BigDecimal.TEN, BigDecimal.TEN, "ref", false);
    }

    private static HttpClientErrorException rechazo(HttpStatus status, String codigo) {
        String body = "{\"codigo\":\"" + codigo + "\",\"detail\":\"rechazado\"}";
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    void transferenciaExitosaDebitaYAcredita() throws Exception {
        when(cuentas.aplicarMovimiento(eq(1L), eq("DEBITO"), any(), endsWith("-D"), any())).thenReturn(ok(1, 1));
        when(cuentas.aplicarMovimiento(eq(3L), eq("CREDITO"), any(), endsWith("-C"), any())).thenReturn(ok(3, 2));

        mvc.perform(post("/api/pagos/transferencias").with(servicioInterno()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaOrigenId":1,"cuentaDestinoId":3,"monto":25000,"canal":"web"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("COMPLETADO"))
                .andExpect(jsonPath("$.canal").value("WEB"));
    }

    @Test
    void saldoInsuficienteRechazaSinCompensar() throws Exception {
        when(cuentas.aplicarMovimiento(eq(4L), eq("DEBITO"), any(), any(), any()))
                .thenThrow(rechazo(HttpStatus.UNPROCESSABLE_ENTITY, "SALDO_INSUFICIENTE"));

        mvc.perform(post("/api/pagos/transferencias").with(servicioInterno()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaOrigenId":4,"cuentaDestinoId":1,"monto":999999}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivo").value("SALDO_INSUFICIENTE: rechazado"));
        verify(cuentas, never()).aplicarMovimiento(any(), eq("CREDITO"), any(), any(), any());
    }

    @Test
    void destinoCerradoCompensaElDebito() throws Exception {
        when(cuentas.aplicarMovimiento(eq(2L), eq("DEBITO"), any(), endsWith("-D"), any())).thenReturn(ok(2, 1));
        when(cuentas.aplicarMovimiento(eq(9L), eq("CREDITO"), any(), endsWith("-C"), any()))
                .thenThrow(rechazo(HttpStatus.CONFLICT, "CUENTA_NO_ACTIVA"));
        when(cuentas.aplicarMovimiento(eq(2L), eq("CREDITO"), any(), endsWith("-R"), any())).thenReturn(ok(2, 1));

        mvc.perform(post("/api/pagos/transferencias").with(servicioInterno()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cuentaOrigenId":2,"cuentaDestinoId":9,"monto":1000}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estado").value("REVERTIDO"));
        // Se devolvió el dinero a la cuenta origen
        verify(cuentas).aplicarMovimiento(eq(2L), eq("CREDITO"), any(), endsWith("-R"), any());
    }

    @Test
    void servicioCaidoDejaPendienteYLuegoRechaza() throws Exception {
        when(cuentas.aplicarMovimiento(eq(5L), any(), any(), any(), any()))
                .thenThrow(new CuentasNoDisponibleException("caido", new RuntimeException()));

        mvc.perform(post("/api/pagos/retiros").with(servicioInterno()).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "ATM-TEST-1")
                        .content("""
                                {"cuentaOrigenId":5,"monto":20000,"canal":"atm"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        // Reintento del planificador: alcanza el máximo (2) sin haber debitado -> RECHAZADO
        var pago = servicio.ejecutar(pagos.findByReferencia("ATM-TEST-1").orElseThrow());
        assertThat(pago.getEstado().name()).isEqualTo("RECHAZADO");
    }

    @Test
    void idempotencyKeyNoDuplicaOperacion() throws Exception {
        when(cuentas.aplicarMovimiento(eq(1L), any(), any(), any(), any())).thenReturn(ok(1, 1));
        String body = """
                {"cuentaOrigenId":1,"monto":5000,"descripcion":"Luz","canal":"movil"}""";

        mvc.perform(post("/api/pagos/servicios").with(servicioInterno()).header("Idempotency-Key", "MOV-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pagos/servicios").with(servicioInterno()).header("Idempotency-Key", "MOV-123")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referencia").value("MOV-123"));
        verify(cuentas, times(1)).aplicarMovimiento(eq(1L), eq("DEBITO"), any(), eq("MOV-123-D"), any());
    }
}
