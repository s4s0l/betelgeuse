create table mappping_table (
    i integer,
    s string,
    nested_object object(strict) as (
         i integer,
         s string,
         o object as (
           val timestamp
         ),
         a array(string),
          la array(string),
          loa array(object as (i integer, s string)),
          ms object,
          moa object
       ),
    a array(string),
    oa array(object as (i integer, s string)),
    la array(string),
    loa array(object as (i integer, s string)),
    ms object,
    moa object
);

