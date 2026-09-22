use oxigraph::model::{
    BlankNode, GraphName, Literal, NamedNode, NamedOrBlankNode, Quad, Term,
};
use oxigraph::sparql::results::QuerySolutionRef;
use oxigraph::sparql::{
    IncrementalSelectResultsState, PreparedSparqlQuery,
    SparqlEvaluator,
};
use oxigraph::store::{Store, Transaction};
use std::ffi::{CStr, c_char};
use std::pin::Pin;
use std::time::{Duration, Instant};

pub struct QueryEvaluator {
    // we pin the store as we keep a reference to it in our transaction & evaluator, which is
    //  instantiated before we're fully constructed
    store: Pin<Box<Store>>,
    last_evaluation: Option<Evaluation>,
    // the transaction is tied to our store, but we can't explicitly state this,
    //  so we use 'static lifetime and transmute it
    transaction: Option<Transaction<'static>>,
    // same deal here
    evaluator:
        IncrementalSelectResultsState<'static, oxigraph::sparql::dataset::DatasetView<'static>>,
}

#[derive(Debug)]
pub struct Evaluation {
    pub duration: Duration,
    pub checksum: i32,
    pub count: i32,
}

impl QueryEvaluator {
    fn new(query: &str) -> Self {
        let store = Box::pin(Store::new().unwrap());
        let mut result = Self {
            last_evaluation: None,
            transaction: None,
            evaluator: Self::parse_query(query)
                .on_store(&store)
                .execute_incremental_results()
                .expect("Failed to initiate the incremental result state!"),
            store,
        };
        result.configure_transaction();

        result
    }

    fn eval(&mut self) {
        self.transaction
            .take()
            .expect("Invalid query evaluator state: missing transaction!")
            .commit()
            .expect("Failed to commit changes!");

        let start = Instant::now();
        let mut count = 0;
        let mut checksum = 0;
        let query_solutions = self.evaluator
            .results()
            .expect("Failed to evaluate query!")
            .collect::<Vec<QuerySolutionRef>>();
        let duration = start.elapsed();
        for solution in query_solutions {
            checksum += Self::get_checksum(solution);
            count += 1;
        }
        let evaluation = Evaluation {
            duration,
            checksum,
            count,
        };
        let _ = self.last_evaluation.insert(evaluation);
        // resetting transaction state
        self.configure_transaction();
    }

    fn parse_query(query: &str) -> PreparedSparqlQuery {
        SparqlEvaluator::new()
            .parse_query(query)
            .expect("Failed to parse query")
    }

    fn configure_transaction(&mut self) {
        // SAFETY: this is valid as we tie it to our own store, which is valid for the duration of
        //  `self`, and is not used outside of it
        self.transaction = unsafe {
            std::mem::transmute(Some(
                self.store
                    .start_transaction()
                    .expect("Failed to start new transaction!"),
            ))
        };
    }

    fn get_checksum(query_solution: QuerySolutionRef) -> i32 {
        let mut result: i32 = 0;
        query_solution.iter().for_each(|v| {
            result = result.overflowing_add(Self::get_checksum_value(v.1)).0;
        });
        result
    }

    fn get_checksum_value(term: &Term) -> i32 {
        match term {
            Term::NamedNode(a) => a.as_str().len() as i32,
            Term::BlankNode(_) => 1,
            Term::Literal(a) => a.value().len() as i32,
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn create_evaluator(query: *const c_char) -> *mut QueryEvaluator {
    let query = unsafe { CStr::from_ptr(query) }.to_str().unwrap();
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

#[derive(Clone)]
pub enum Node {
    NamedNode(NamedNode),
    BlankNode(BlankNode),
    Literal(Literal),
}

impl From<Node> for NamedOrBlankNode {
    fn from(node: Node) -> Self {
        match node {
            Node::NamedNode(node) => NamedOrBlankNode::NamedNode(node),
            Node::BlankNode(node) => NamedOrBlankNode::BlankNode(node),
            Node::Literal(_) => panic!("Wrong node type encountered!"),
        }
    }
}

impl From<Node> for NamedNode {
    fn from(node: Node) -> NamedNode {
        match node {
            Node::NamedNode(node) => node,
            Node::BlankNode(_) | Node::Literal(_) => panic!("Wrong node type encountered!"),
        }
    }
}

impl From<Node> for Term {
    fn from(node: Node) -> Term {
        match node {
            Node::NamedNode(node) => Term::NamedNode(node),
            Node::BlankNode(node) => Term::BlankNode(node),
            Node::Literal(node) => Term::Literal(node),
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn create_named_node(uri: *const c_char) -> *mut Node {
    Box::into_raw(Box::new(Node::NamedNode(
        NamedNode::new(unsafe { CStr::from_ptr(uri) }.to_str().unwrap()).unwrap(),
    )))
}

#[unsafe(no_mangle)]
pub extern "C" fn create_blank_node(id: u32) -> *mut Node {
    Box::into_raw(Box::new(Node::BlankNode(
        BlankNode::new(id.to_string()).unwrap(),
    )))
}

#[unsafe(no_mangle)]
pub extern "C" fn create_typed_literal_node(
    value: *const c_char,
    dtype: *const c_char,
) -> *mut Node {
    Box::into_raw(Box::new(Node::Literal(Literal::new_typed_literal(
        unsafe { CStr::from_ptr(value) }.to_str().unwrap(),
        NamedNode::new(unsafe { CStr::from_ptr(dtype).to_str().unwrap() }).unwrap(),
    ))))
}

#[unsafe(no_mangle)]
pub extern "C" fn create_lang_literal_node(value: *const c_char, tag: *const c_char) -> *mut Node {
    Box::into_raw(Box::new(Node::Literal(
        Literal::new_language_tagged_literal_unchecked(
            unsafe { CStr::from_ptr(value) }.to_str().unwrap(),
            unsafe { CStr::from_ptr(tag).to_str().unwrap() },
        ),
    )))
}

#[unsafe(no_mangle)]
pub extern "C" fn dispose_node(value: *mut Node) {
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

fn create_quad(s: *mut Node, p: *mut Node, o: *mut Node) -> Quad {
    Quad::new(
        NamedOrBlankNode::from(unsafe { s.as_mut() }.cloned().unwrap()),
        NamedNode::from(unsafe { p.as_mut() }.cloned().unwrap()),
        Term::from(unsafe { o.as_mut() }.cloned().unwrap()),
        GraphName::default(),
    )
}

#[unsafe(no_mangle)]
pub extern "C" fn insert_quad(
    query_evaluator: *mut QueryEvaluator,
    s: *mut Node,
    p: *mut Node,
    o: *mut Node,
) {
    let evaluator = unsafe { query_evaluator.as_mut() }.unwrap();
    let quad = create_quad(s, p, o);
    evaluator
        .transaction
        .as_mut()
        .expect("Failed to insert quad - transaction missing!")
        .insert(quad);
}

#[unsafe(no_mangle)]
pub extern "C" fn remove_quad(
    query_evaluator: *mut QueryEvaluator,
    s: *mut Node,
    p: *mut Node,
    o: *mut Node,
) {
    let evaluator = unsafe { query_evaluator.as_mut() }.unwrap();
    let quad = create_quad(s, p, o);
    evaluator
        .transaction
        .as_mut()
        .expect("Failed to remove quad - transaction missing!")
        .remove(&quad);
}
