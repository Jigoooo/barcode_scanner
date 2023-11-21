import {configureStore} from "@reduxjs/toolkit";
import counterReducer from "./reducers/counterReducer";

export default configureStore({
    reducer: {
        counter: counterReducer,
    }
});
