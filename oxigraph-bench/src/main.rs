mod result_file;
mod evaluation;

use clap::Parser;
use oxigraph::io::{RdfFormat, RdfParser};
use oxigraph::sparql::{
    PreparedSparqlQuery, SparqlEvaluator
    ,
};
use oxigraph::store::Store;
use std::fs;
use std::fs::File;
use std::path::PathBuf;
use crate::evaluation::Evaluation;
use crate::result_file::ResultFile;

#[derive(Parser, Debug)]
#[command(version, about, long_about = None)]
struct Args {
    #[arg(short, long)]
    query: PathBuf,
    #[arg(short, long)]
    filepaths: Vec<PathBuf>,
}

fn bail(reason: String) -> ! {
    eprintln!("{}", reason);
    std::process::exit(1);
}

macro_rules! bail {
    ($($v:expr),+) => {
        bail(std::fmt::format(format_args!($($v), +)))
    };
}

fn parse_query(path: &PathBuf) -> PreparedSparqlQuery {
    let evaluator = SparqlEvaluator::new();
    let query = match fs::read_to_string(path) {
        Ok(query) => query,
        Err(e) => panic!("Failed to read file {}: {}", path.display(), e),
    };
    evaluator
        .parse_query(&query)
        .expect("Failed to parse query")
}

fn insert_data(store: &Store, path: &PathBuf) {
    println!("Reading file {}", path.display());
    let file = File::open(path).expect("Failed to open file");
    let original_size = store.len().expect("Failed to read original size");
    let mut loader = store.bulk_loader();
    loader
        .load_from_reader(RdfParser::from_format(RdfFormat::TriG), file)
        .expect("Failed to add quads!");
    loader
        .commit()
        .expect("Failed to commit the new quads from file!");
    println!("Added {} quads!", store.len().expect("Failed to read the new store length") - original_size);
}

fn derive_output_filename(query: &PathBuf) -> String {
    format!("{}_output.csv", query.file_stem().unwrap().to_str().unwrap())
}

fn main() {
    let args = Args::parse();
    if !args.query.is_file() {
        bail!("Invalid argument: {}", args.query.display());
    }
    let query = parse_query(&args.query);

    let store = Store::new().expect("Failed to create a new in-memory store");
    let mut result_file = ResultFile::new(derive_output_filename(&args.query)).expect("Failed to create a result file");
    args.filepaths.iter().for_each(|filepath| {
        insert_data(&store, filepath);
        let result = Evaluation::from(query.clone(), &store).expect("Failed to evaluate solution");
        println!("Got result {:?}", result);
        result_file.append(result).expect("Failed to append result");
    })
}
