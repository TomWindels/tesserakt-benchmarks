use oxigraph::io::{RdfFormat, RdfParser, ReaderQuadParser};
use oxigraph::model::Quad;
use oxigraph::store::Store;
use std::fs::File;
use std::path::PathBuf;

pub struct DatasetParser {
    step_size: usize,
    src: ReaderQuadParser<File>,
}

impl DatasetParser {
    pub fn new(path: PathBuf) -> Result<Self, std::io::Error> {
        let file = File::open(path)?;
        let src = RdfParser::from_format(RdfFormat::TriG).for_reader(file);
        let result = Self { step_size: 512, src, };
        Ok(result)
    }

    pub fn insert_into(&mut self, store: &Store) -> usize {
        let original_size = store.len().expect("Failed to read original size");
        let mut loader = store.bulk_loader();
        loader
            .load_quads(self)
            .expect("Failed to add quads!");
        loader
            .commit()
            .expect("Failed to commit the new quads from file!");
        let added = store.len().expect("Failed to read the new store length") - original_size;
        println!("Added {} quads!", added);
        added
    }
}

pub struct DatasetIterator<'a> {
    parent: &'a mut DatasetParser,
    remaining: usize,
}

impl<'a> IntoIterator for &'a mut DatasetParser {
    type Item = Quad;
    type IntoIter = DatasetIterator<'a>;

    fn into_iter(self) -> Self::IntoIter {
        DatasetIterator {
            remaining: self.step_size,
            parent: self,
        }
    }
}

impl Iterator for DatasetIterator<'_> {
    type Item = Quad;
    fn next(&mut self) -> Option<<Self as Iterator>::Item> {
        if self.remaining > 0 {
            self.remaining -= 1;
            self.parent.src.next().map(|q| q.unwrap())
        } else {
            None
        }
    }
}
