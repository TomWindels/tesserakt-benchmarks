use std::time::{Duration, Instant};
use oxigraph::model::Term;
use oxigraph::sparql::{PreparedSparqlQuery, QueryEvaluationError, QueryResults, QuerySolution};
use oxigraph::store::Store;

#[derive(Debug)]
pub struct Evaluation {
    pub index: u32,
    pub duration: Duration,
    pub checksum: i32,
    pub count: i32,
}

impl Evaluation {
    pub fn from(
        index: u32,
        query: PreparedSparqlQuery,
        store: &Store,
    ) -> Result<Evaluation, QueryEvaluationError> {
        let start = Instant::now();
        let mut count = 0;
        let mut checksum = 0;
        if let QueryResults::Solutions(solutions) = query.on_store(&store).execute()? {
            for solution in solutions {
                let solution = solution?;
                checksum += Self::get_checksum(solution);
                count += 1;
            }
            Ok(Evaluation {
                index,
                duration: Instant::now() - start,
                checksum,
                count,
            })
        } else {
            Err(QueryEvaluationError::Cancelled)
        }
    }

    fn get_checksum_value(term: &Term) -> i32 {
        match term {
            Term::NamedNode(a) => a.as_str().len() as i32,
            Term::BlankNode(_) => 1,
            Term::Literal(a) => a.value().len() as i32,
        }
    }

    fn get_checksum(query_solution: QuerySolution) -> i32 {
        let mut result: i32 = 0;
        query_solution.iter().for_each(|v| {
            result = result.overflowing_add(Self::get_checksum_value(v.1)).0;
        });
        result
    }
}
