use oxigraph::model::{BlankNode, GraphName, Literal, NamedNode, NamedOrBlankNode, Quad, Term};
use oxigraph::sparql::{PreparedSparqlQuery, QueryResults, QuerySolution, SparqlEvaluator};
use oxigraph::store::Store;
use std::ffi::{CStr, c_char};
use std::time::{Duration, Instant};

pub struct QueryEvaluator {
    query: PreparedSparqlQuery,
    store: Store,
    last_evaluation: Option<Evaluation>,
}

#[derive(Debug)]
pub struct Evaluation {
    pub duration: Duration,
    pub checksum: i32,
    pub count: i32,
}

impl QueryEvaluator {
    fn new(query: &str) -> Self {
        Self {
            query: Self::parse_query(query),
            store: Store::new().unwrap(),
            last_evaluation: None,
        }
    }

    fn eval(&mut self) {
        let start = Instant::now();
        let mut count = 0;
        let mut checksum = 0;
        let query_solutions = if let QueryResults::Solutions(solutions) =
            self.query.clone().on_store(&self.store).execute().unwrap()
        {
            solutions
                .map(|r| r.unwrap())
                .collect::<Vec<QuerySolution>>()
        } else {
            panic!()
        };
        let duration = start.elapsed();
        for solution in query_solutions {
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

    fn parse_query(query: &str) -> PreparedSparqlQuery {
        SparqlEvaluator::new()
            .parse_query(query)
            .expect("Failed to parse query")
    }

    fn get_checksum(query_solution: QuerySolution) -> i32 {
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
pub extern "C" fn create_typed_literal_node(value: *const c_char, dtype: *const c_char) -> *mut Node {
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
pub extern "C" fn insert_quad(query_evaluator: *mut QueryEvaluator, s: *mut Node, p: *mut Node, o: *mut Node) {
    let evaluator = unsafe { query_evaluator.as_mut() }.unwrap();
    let quad = create_quad(s, p, o);
    evaluator.store.insert(&quad).expect("Failed to insert quad");
}

#[unsafe(no_mangle)]
pub extern "C" fn remove_quad(query_evaluator: *mut QueryEvaluator, s: *mut Node, p: *mut Node, o: *mut Node) {
    let evaluator = unsafe { query_evaluator.as_mut() }.unwrap();
    let quad = create_quad(s, p, o);
    evaluator.store.remove(&quad).expect("Failed to remove quad");
}
