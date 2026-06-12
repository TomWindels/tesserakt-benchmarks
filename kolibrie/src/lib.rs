use kolibrie::execute_query::{execute_query, execute_query_rayon_parallel2_volcano};
use kolibrie::sparql_database::SparqlDatabase;
use std::ffi::{c_char, CStr};
use std::time::{Duration, Instant};

pub struct QueryEvaluator {
    query: String,
    store: SparqlDatabase,
    last_evaluation: Option<Evaluation>,
}

#[derive(Debug)]
pub struct Evaluation {
    pub duration: Duration,
    pub checksum: i32,
    pub count: i32,
}

impl QueryEvaluator {
    fn new(query: String) -> Self {
        Self {
            query,
            store: SparqlDatabase::new(),
            last_evaluation: None,
        }
    }

    fn eval(&mut self) {
        let start = Instant::now();
        let mut count = 0;
        let mut checksum = 0;
        // the use of the non-deprecated version causes memory issues when evaluating with
        // `railway-batch-2-inferred_batch_connected_segments_inject_connected_segments_repair_connected_segments-25.ttl`
        // let query_solutions = execute_query_rayon_parallel2_volcano(&self.query, &mut self.store);
        let query_solutions = execute_query(&self.query, &mut self.store);
        let duration = start.elapsed();
        for solution in &query_solutions {
            let solution = solution;
            checksum += Self::get_checksum(solution);
            count += 1;
        }
        let evaluation = Evaluation {
            duration,
            checksum,
            count,
        };
        let _ = self.last_evaluation.insert(evaluation);
    }

    fn get_checksum(query_solution: &Vec<String>) -> i32 {
        let mut result: i32 = 0;
        query_solution.iter().for_each(|v| {
            // FIXME not sure how literals & blank nodes are being represented as a string, but
            //  they are 100% not being properly transformed in their checksum value
            result = result.overflowing_add(v.len() as i32).0;
        });
        result
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn create_evaluator(query: *const c_char) -> *mut QueryEvaluator {
    let query = unsafe { CStr::from_ptr(query) }.to_str().unwrap().to_owned();
    Box::into_raw(Box::new(QueryEvaluator::new(query)))
}

#[unsafe(no_mangle)]
pub extern "C" fn exec_evaluator(evaluator: *mut QueryEvaluator) {
    unsafe { evaluator.as_mut() }.unwrap().eval();
}

#[unsafe(no_mangle)]
pub extern "C" fn get_last_duration(evaluator: *mut QueryEvaluator) -> f64 {
    unsafe { evaluator.as_ref() }
        .unwrap()
        .last_evaluation
        .as_ref()
        .unwrap()
        .duration
        .as_secs_f64()
}

#[unsafe(no_mangle)]
pub extern "C" fn get_last_checksum(evaluator: *mut QueryEvaluator) -> i32 {
    unsafe { evaluator.as_ref() }
        .unwrap()
        .last_evaluation
        .as_ref()
        .unwrap()
        .checksum
}

#[unsafe(no_mangle)]
pub extern "C" fn get_last_count(evaluator: *mut QueryEvaluator) -> i32 {
    unsafe { evaluator.as_ref() }
        .unwrap()
        .last_evaluation
        .as_ref()
        .unwrap()
        .count
}

#[unsafe(no_mangle)]
pub extern "C" fn create_named_node(uri: *const c_char) -> *mut String {
    // we can leak it here; the cleanup happens in dedicated callbacks
    Box::leak(Box::new(unsafe { CStr::from_ptr(uri) }.to_str().unwrap().to_owned()))
}

#[unsafe(no_mangle)]
pub extern "C" fn create_blank_node(id: u32) -> *mut String {
    // we can leak it here; the cleanup happens in dedicated callbacks
    Box::leak(Box::new(id.to_string()))
}

#[unsafe(no_mangle)]
pub extern "C" fn create_typed_literal_node(value: *const c_char, _dtype: *const c_char) -> *mut String {
    Box::leak(Box::new(unsafe { CStr::from_ptr(value) }.to_str().unwrap().to_owned()))
}

#[unsafe(no_mangle)]
pub extern "C" fn create_lang_literal_node(value: *const c_char, _tag: *const c_char) -> *mut String {
    Box::leak(Box::new(unsafe { CStr::from_ptr(value) }.to_str().unwrap().to_owned()))
}

#[unsafe(no_mangle)]
pub extern "C" fn dispose_node(value: *mut String) {
    unsafe {
        let _ = Box::from_raw(value);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn dispose_evaluator(value: *mut QueryEvaluator) {
    unsafe {
        let _ = Box::from_raw(value);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn insert_quad(query_evaluator: *mut QueryEvaluator, s: *mut String, p: *mut String, o: *mut String) {
    let evaluator = unsafe { query_evaluator.as_mut() }.unwrap();
    unsafe {
        evaluator.store.add_triple_parts(&*s, &*p, &*o);
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn remove_quad(query_evaluator: *mut QueryEvaluator, s: *mut String, p: *mut String, o: *mut String) {
    let evaluator = unsafe { query_evaluator.as_mut() }.unwrap();
    unsafe {
        evaluator.store.delete_triple_parts(&*s, &*p, &*o);
    }
}
