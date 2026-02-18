module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [2, 'always', ['feat', 'fix', 'chore', 'docs', 'test', 'refactor']],
    'subject-case': [0]
  },
  parserPreset: {
    parserOpts: {
      headerPattern: /^(\w+)(?:\(([^)]+)\))?!?: (.+)$/,
      headerCorrespondence: ['type', 'scope', 'subject']
    }
  }
}
