use std::fs;
use std::fs::File;
use std::io::{BufWriter, Write};
use std::path::PathBuf;
use crate::evaluation::Evaluation;

pub struct ResultFile {
    buf: BufWriter<File>,
}

impl ResultFile {
    pub fn new<P: Into<PathBuf>>(path: P) -> Result<ResultFile, std::io::Error> {
        let file = Self::init_results_file(&path.into())?;
        Ok(ResultFile { buf: BufWriter::new(file) })
    }

    pub fn append(&mut self, evaluation: Evaluation) -> Result<(), std::io::Error> {
        Self::write_results(&mut self.buf, evaluation)
    }

    fn init_results_file(name: &PathBuf) -> Result<File, std::io::Error> {
        if fs::exists(name)? {
            return Err(std::io::Error::new(
                std::io::ErrorKind::AlreadyExists,
                name.to_str().unwrap_or("?"),
            ));
        }
        let mut file = File::create(name)?;
        file.write("duration(ms),total,checksum".as_bytes())?;
        Ok(file)
    }

    fn write_results(writer: &mut BufWriter<File>, results: Evaluation) -> Result<(), std::io::Error> {
        writer
            .write_fmt(format_args!(
                "\n{},{},{}",
                results.duration.as_micros() as f32 / 1000f32,
                results.count,
                results.checksum
            ))
    }

}
