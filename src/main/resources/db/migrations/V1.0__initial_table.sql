create sequence spabehandling_id_seq;

create table spabehandling
(
  id bigint not null default nextval('spabehandling_id_seq'),
  soknad_sendt_nav timestamp not null,
  spa_vurderingstidspunkt timestamp not null,
  spa_vedtak text not null,
  opprettet timestamp default localtimestamp,
  endret timestamp,
  neste_forsoek_ikke_foer timestamp,
  avstemming_resultat varchar(100),
  avstemming_detaljer text,
  constraint pk_spabehandling primary key (id)
);

create index spabehandling_spa_vurderingstidspunkt_idx on spabehandling(spa_vurderingstidspunkt);
create index spabehandling_avstemming_resultat_idx on spabehandling(avstemming_resultat);