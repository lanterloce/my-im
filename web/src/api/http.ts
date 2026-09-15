import axios from 'axios'; import {getAccessToken} from '../utils/auth';
const http=axios.create({baseURL:'http://localhost:8080',timeout:10000,headers:{'Content-Type':'application/json'}});
http.interceptors.request.use(config=>{const token=getAccessToken();if(token)config.headers.Authorization=`Bearer ${token}`;return config});
export default http;
