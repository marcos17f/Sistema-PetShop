package br.com.marcoslab.petshop.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    @Query("""
            select a from Agendamento a join fetch a.pet p join fetch p.tutor
            where a.loja.id = :lojaId and a.data = :data
            order by a.hora
            """)
    List<Agendamento> doDia(@Param("lojaId") Long lojaId, @Param("data") LocalDate data);

    /** Atendimentos que ocupam a agenda no dia (cancelado não ocupa). Poucos por dia: o intervalo é conferido em Java. */
    @Query("""
            select a from Agendamento a
            where a.loja.id = :lojaId and a.data = :data
              and a.status <> br.com.marcoslab.petshop.agenda.StatusAgendamento.CANCELADO
            """)
    List<Agendamento> ativosDoDia(@Param("lojaId") Long lojaId, @Param("data") LocalDate data);

    @Query("select a from Agendamento a join fetch a.pet p join fetch p.tutor where a.id = :id and a.loja.id = :lojaId")
    Optional<Agendamento> buscarNaLoja(@Param("id") Long id, @Param("lojaId") Long lojaId);

    /** Atendimentos concluídos no período (receita do financeiro). */
    @Query("""
            select a from Agendamento a join fetch a.pet p join fetch p.tutor
            where a.loja.id = :lojaId and a.data between :inicio and :fim
              and a.status = br.com.marcoslab.petshop.agenda.StatusAgendamento.CONCLUIDO
            order by a.data, a.hora
            """)
    List<Agendamento> concluidosEntre(@Param("lojaId") Long lojaId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
            select count(a) > 0 from Agendamento a
            where a.pet.tutor.id = :tutorId and a.status = br.com.marcoslab.petshop.agenda.StatusAgendamento.CONCLUIDO
            """)
    boolean tutorTemConcluido(@Param("tutorId") Long tutorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Agendamento a where a.pet.id in (select p.id from Pet p where p.tutor.id = :tutorId)")
    void apagarDoTutor(@Param("tutorId") Long tutorId);

    /** Cliente removido: os horários futuros dele voltam a ficar livres. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Agendamento a set a.status = br.com.marcoslab.petshop.agenda.StatusAgendamento.CANCELADO
            where a.pet.id in (select p.id from Pet p where p.tutor.id = :tutorId) and a.data >= :hoje
              and a.status <> br.com.marcoslab.petshop.agenda.StatusAgendamento.CONCLUIDO
            """)
    void cancelarFuturosDoTutor(@Param("tutorId") Long tutorId, @Param("hoje") LocalDate hoje);
}
