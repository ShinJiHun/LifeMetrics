// src/main.tsx
// 삭제
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AdminProvider } from "./lib/admin";
import "./index.css";

const rootElement = document.getElementById("root");
if (rootElement) {
    ReactDOM.createRoot(rootElement).render(
        <BrowserRouter>
            <AdminProvider>
                <App />
            </AdminProvider>
        </BrowserRouter>
    );
}
