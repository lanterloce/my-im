import http from './http'; import {saveTokens,clearTokens,getRefreshToken} from '../utils/auth';
export async function login(data:{username:string;password:string}){const r=await http.post('/api/auth/login',data);saveTokens(r.data.accessToken,r.data.refreshToken);return r.data}
export async function register(data:{username:string;password:string;displayName?:string}){return (await http.post('/api/auth/register',data)).data}
export async function refresh(){const r=await http.post('/api/auth/refresh',{refreshToken:getRefreshToken()});saveTokens(r.data.accessToken,r.data.refreshToken);return r.data}
export function logout(){clearTokens()}
