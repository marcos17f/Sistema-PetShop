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

    @Modifying
    @Query("delete from MensagemWhatsApp m where m.criadoEm < :antes")
    int apagarAntesDe(@Param("antes") Instant antes);
}
