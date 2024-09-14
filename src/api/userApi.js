import axios from 'axios';

const API_URL = '/api/auth';

const userApi = {
  register: (user) => axios.post(`${API_URL}/register`, user),
  
  login: (credentials) => axios.post(`${API_URL}/login`, credentials),
  
  getUser: () => axios.get(`${API_URL}/user`),
  
  checkLoginStatus: () => axios.get(`${API_URL}/public/checkLoginStatus`)
};

export default userApi;