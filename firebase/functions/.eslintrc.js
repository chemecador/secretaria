module.exports = {
  root: true,
  env: {
    es6: true,
    node: true,
  },
  extends: [
    "eslint:recommended",
    "plugin:import/errors",
    "plugin:import/warnings",
    "plugin:import/typescript",
    "google",
    "plugin:@typescript-eslint/recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    project: ["tsconfig.json", "tsconfig.dev.json"],
    sourceType: "module",
  },
  ignorePatterns: [
    "/lib/**/*",
    "/generated/**/*",
  ],
  plugins: [
    "@typescript-eslint",
    "import",
  ],
  rules: {
    "quotes": ["error", "double"],
    "import/no-unresolved": 0,
    "indent": ["error", 2],
    // Los finales de linea los decide git: `.gitattributes` normaliza a LF en el repo y
    // `core.autocrlf` deja CRLF en disco en Windows. Sin esto, `npm run lint` marca el
    // fichero entero y el `predeploy` de `firebase deploy` no arranca en ese equipo.
    "linebreak-style": "off",
    "max-len": "off",
    "object-curly-spacing": ["error", "always"],
  },
};
