import { render } from "preact";
import "./theme.css";
import "./app.css";
import { App } from "./app";
import * as api from "./lib/api";

api.init();
render(<App />, document.getElementById("app")!);
