# Lucifero

Simple static website generator written in Clojure.

## Customization

Lucifero uses a `config.clj` file defined in the root of the project. Here there's a  base example to start with.

```clojure
(defwebsite lucifero "1.0.0"
  :title       "Lucifero"
  :description "Generate static webpage for Lucifero"
  :baseurl     "https://www.example.com"
  :directory   "resources"
  :pages-dir   "pages"
  :public-dir  "public"
  :layouts-dir "layouts"
  :dest-dir    "dist"
  :variables   {:foo "bar"})
```

## Folder structure

Lucifero follow a customizable directory structure.
```
mywebsite/
├── layouts/
│   ├── includes/
│   │   └── header.html
│   └── default.html
├── pages/
│   ├── about.md
│   └── index.md
├── public/
│   ├── css/
│   ├── js/
│   └── imgs/
└── config.clj
```
Only 3 directories are important:

- `public` contains all the public resources that needs to be copied inside the distributable folder. Layouts file could refer to them.
- `pages` contains the content of the website, and support both markdown `.md` and org `.org` file format. 
- `layouts` contains all the layouts file used to templates the content.

## Pages

You can create all the pages you want inside `pages` directory.

## Development

- `lein run` to run the project with dev environment
- `lein uberjar` to create a release

## TODO
- Add org support
- Add subdirectory support for `pages`

## License

See the [LICENSE](https://github.com/elias94/lucifero/blob/main/LICENSE) file.
