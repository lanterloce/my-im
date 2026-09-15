<script setup lang="ts">
import {ref,onMounted} from 'vue'
import axios from 'axios'
const api = axios.create({
  baseURL: 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json'
  }
})
const username=ref('demo');const password=ref('123456');const logged=ref(false);const token=ref('');const currentUser = ref<any>(null);const content=ref('');const messages=ref<any[]>([]);const conversationId=1
async function login() {
  try {
    const r = await api.post('/api/users/login', {
      username: username.value,
      password: password.value
    });

    token.value = r.data.token;

    // 保存当前登录用户
    currentUser.value = r.data.user || {
      id: r.data.userId,
      username: username.value,
      displayName: username.value
    };

    logged.value = true;
    await load();
  } catch (e) {
    alert('登录失败：请先注册或检查后端');
  }
}
async function register(){try{await api.post('/api/users/register',{username:username.value,password:password.value});alert('注册成功，请登录')}catch(e){alert('注册失败')}} 
async function load(){messages.value=(await api.get('/api/messages/'+conversationId)).data.reverse()}
async function send() {
  if (!content.value.trim()) return;

  if (!currentUser.value?.id) {
    alert('当前用户信息不存在，请重新登录');
    return;
  }

  await api.post('/api/messages', {
    conversationId,
    senderId: currentUser.value.id,
    content: content.value
  });

  content.value = '';
  await load();
}
onMounted(()=>{})
</script>
<template><div class="page"><div v-if="!logged" class="login"><h1>Enterprise IM</h1><p>企业级即时通信平台</p><el-input v-model="username" placeholder="用户名"/><el-input v-model="password" type="password" placeholder="密码" show-password/><div><el-button type="primary" @click="login">登录</el-button><el-button @click="register">注册</el-button></div></div><div v-else class="chat"><header><b>Enterprise IM</b><span>产品研发群</span></header><main><div v-for="m in messages" :key="m.id" class="msg"><b>用户{{m.senderId}}</b><p>{{m.content}}</p></div></main><footer><el-input v-model="content" placeholder="输入消息" @keyup.enter="send"/><el-button type="primary" @click="send">发送</el-button></footer></div></div></template>