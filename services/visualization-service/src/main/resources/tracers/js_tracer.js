/**
 * AlgoVerse JavaScript execution tracer.
 * Uses vm2-style isolation via Node's vm module.
 * Reads code from stdin, emits JSON trace to stdout.
 */
'use strict';

const vm = require('vm');
const readline = require('readline');

const MAX_STEPS = parseInt(process.argv[2] || '500', 10);

let raw = '';
process.stdin.on('data', d => raw += d);
process.stdin.on('end', () => {
  const [codePart, inputPart = ''] = raw.split('---INPUT---');
  const code = codePart.trim();

  const steps = [];
  let stepCount = 0;
  const outputLines = [];
  let error = null;

  // Instrument: wrap each statement by injecting a step recorder.
  // We use a line-level approach via Proxy-trapped console + simple AST walk.
  // For production, use acorn + escodegen; here we use a lightweight approach.

  const recordStep = (line, fn, locals) => {
    if (stepCount >= MAX_STEPS) return;
    stepCount++;
    steps.push({ step: stepCount, event: 'line', line, function: fn, locals, stdout: outputLines.join('\n') });
  };

  const sandbox = {
    console: {
      log: (...args) => { outputLines.push(args.map(String).join(' ')); },
      error: (...args) => { outputLines.push('[err] ' + args.map(String).join(' ')); },
      warn: (...args) => { outputLines.push('[warn] ' + args.map(String).join(' ')); },
    },
    __record: recordStep,
    __input: inputPart.trim(),
    process: { argv: [], env: {} },
    require: () => { throw new Error('require() is not allowed in sandbox'); },
  };

  // Inject __record calls before each statement using regex (simplified)
  let lineNum = 0;
  const instrumented = code.split('\n').map(line => {
    lineNum++;
    const trimmed = line.trim();
    // Skip blank lines, comments, closing braces
    if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed === '}' || trimmed === '{') {
      return line;
    }
    return `__record(${lineNum}, '<global>', {}); ${line}`;
  }).join('\n');

  try {
    const script = new vm.Script(instrumented, { filename: 'user_code.js', lineOffset: 0 });
    const ctx = vm.createContext(sandbox);
    script.runInContext(ctx, { timeout: 8000 });
  } catch (e) {
    error = e.message;
    steps.push({
      step: stepCount + 1,
      event: 'exception',
      line: e.lineNumber || 0,
      function: '<global>',
      locals: {},
      exception: e.message,
      stdout: outputLines.join('\n'),
    });
  }

  const result = {
    language: 'javascript',
    steps,
    finalOutput: outputLines.join('\n'),
    error,
    truncated: stepCount >= MAX_STEPS,
  };

  process.stdout.write(JSON.stringify(result) + '\n');
});
