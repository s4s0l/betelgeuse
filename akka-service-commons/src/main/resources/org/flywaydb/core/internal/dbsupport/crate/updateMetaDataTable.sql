INSERT INTO ${schema}.${table} (
                installed_rank,
                version,
                description,
                type,
                script,
                checksum,
                installed_by,
                execution_time,
                success)
        values (
                ${installed_rank_val},
                '${version_val}',
                '${description_val}',
                '${type_val}',
                '${script_val}',
                ${checksum_val},
                ${installed_by_val},
                ${execution_time_val},
                '${success_val}'
        );

refresh table ${schema}.${table};