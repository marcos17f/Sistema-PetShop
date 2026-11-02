package br.com.marcoslab.petshop.auditoria;

import org.springframework.data.repository.Repository;

import java.util.List;

/** Só grava e lista: de propósito não estende JpaRepository, então não existe delete nem update. */
public interface AuditoriaRepository extends Repository<Auditoria, Long> {

    Auditoria save(Auditoria a);

    List<Auditoria> findTop100ByLojaIdOrderByEmDescIdDesc(Long lojaId);
}
