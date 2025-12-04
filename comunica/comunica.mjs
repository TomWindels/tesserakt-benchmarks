import { createRequire } from 'module';

const require = createRequire(import.meta.url);

function local_require(name) {
    return require(`${import.meta.dirname}/node_modules/${name}`);
}

const comunica = local_require('@comunica/query-sparql');
const RdfStore = local_require("rdf-stores").RdfStore;
const DataFactory = local_require("rdf-data-factory").DataFactory;

const DF = new DataFactory();

function get_checksum_for_term(term) {
    if (term.termType == 'NamedNode') {
        return term.value.length;
    }
    if (term.termType === 'Literal') {
        return term.value.length;
    }
    if (term.termType === 'BlankNode') {
        return 1;
    }
    throw new Error(`Unsupported term type: ${term.termType}`);
}

function get_checksum_for_bindings(binding) {
    let checksum = 0;
    for (const [_, term] of binding) {
        checksum += get_checksum_for_term(term);
    }
    return checksum;
}

export class ComunicaEvaluator {

    constructor(query) {
        this.query = query;
        this.lastDuration = 0;
        this.lastChecksum = 0;
        this.lastCount = 0;
        this.store = RdfStore.createDefault();
        this.engine = new comunica.QueryEngine();
    }

    async run() {
        const start = process.hrtime.bigint();
        const stream = await this.engine.queryBindings(this.query, {
            sources: [this.store],
        });
        const results = await stream.toArray();
        const end = process.hrtime.bigint();
        this.lastChecksum = 0;
        results.forEach((binding) => {
            this.lastChecksum += get_checksum_for_bindings(binding);
        });
        this.lastDuration = Number(end - start) / 1_000_000;
        this.lastCount = results.length;
    }

    getLastDuration() {
        return this.lastDuration;
    }

    getLastChecksum() {
        return this.lastChecksum;
    }

    getLastCount() {
        return this.lastCount;
    }

    createNamedNode(uri) {
        return DF.namedNode(uri);
    }

    createBlankNode(id) {
        return DF.blankNode(`${id}`);
    }

    createTypedLiteralNode(value, dtype) {
        return DF.literal(value, DF.namedNode(dtype));
    }

    createLangLiteralNode(value, lang) {
        return DF.literal(value, lang);
    }

    insertQuad(s, p, o) {
        this.store.addQuad(DF.quad(s, p, o));
    }

    removeQuad(s, p, o) {
        this.store.removeQuad(DF.quad(s, p, o));
    }

}

export async function create(query) {
    return new ComunicaEvaluator(query);
}
