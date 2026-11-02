package br.com.marcoslab.petshop.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MensagemRepository extends JpaRepository<MensagemWhatsApp, Long> {

    List<MensagemWhatsApp> findTop100ByLojaIdOrderByCriadoEmDescIdDesc(Long lojaId);

    /** Já existe mensagem (enviada ou simulada) para esse lembrete? Evita mandar duas vezes. */
    @Query("""
            select count(m) > 0 from MensagemWhatsApp m
            where m.loja.id = :lojaId and m.chave = :chave
              and m.status <> br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Status.FALHOU
            """)
    boolean jaTem(@Param("lojaId") Long lojaId, @Param("chave") String chave);

    @Query("""
            select count(m) from MensagemWhatsApp m
            where m.loja.id = :lojaId and m.status = :status and m.criadoEm >= :desde
            """)
    long contar(@Param("lojaId") Long lojaId, @Param("status") MensagemWhatsApp.Status status, @Param("desde") Instant desde);

    @Query("""
            select count(m) from MensagemWhatsApp m
            where m.loja.id = :lojaId and m.status = br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Status.FALHOU
              and m.criadoEm >= :desde and m.erro like '%401%'
            """)
    long falhasDeAcesso(@Param("lojaId") Long lojaId, @Param("desde") Instant desde);

    @Query("""
            select count(m) > 0 from MensagemWhatsApp m
            where m.loja.id = :lojaId and m.para = :para and m.tipo = :tipo and m.criadoEm >= :desde
            """)
    boolean enviadaDesde(@Param("lojaId") Long lojaId, @Param("para") String para,
                         @Param("tipo") MensagemWhatsApp.Tipo tipo, @Param("desde") Instant desde);

    /** Mensagens de um tipo que saíram (ou foram simuladas) desde um instante: limite diário das campanhas. */
    @Query("""
            select count(m) from MensagemWhatsApp m
            where m.loja.id = :lojaId and m.tipo = :tipo and m.criadoEm >= :desde
              and m.status <> br.com.marcoslab.petshop.whatsapp.MensagemWhatsApp.Status.FALHOU
            """)
    long contarTipo(@Param("lojaId") Long lojaId, @Param("tipo") MensagemWhatsApp.Tipo tipo, @Param("desde") Instant desde);

    @Modifying
    @Query("delete from MensagemWhatsApp m where m.criadoEm < :antes")
    int apagarAntesDe(@Param("antes") Instant antes);
}
