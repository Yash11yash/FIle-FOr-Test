const repoOwner = "Yash11yash";
const repoName = "FIle-FOr-Test";
let token = localStorage.getItem('githubToken') || prompt('Enter your GitHub Personal Access Token:'); // Secure token input
if (token) localStorage.setItem('githubToken', token); // Store temporarily (not secure for production)

const fileIcons = {
    audio: "fas fa-music text-purple-500",
    image: "fas fa-image text-green-500",
    video: "fas fa-video text-red-500",
    pdf: "fas fa-file-pdf text-red-500",
    zip: "fas fa-file-archive text-yellow-500",
    code: "fas fa-file-code text-blue-500",
    text: "fas fa-file-alt text-gray-500",
    default: "fas fa-file text-gray-400"
};

let currentPath = ['root'];
let fileSystem = JSON.parse(localStorage.getItem('fileSystem') || '{"root": {"type": "folder", "children": {}}}');
let draggedFile = null;
let dragOperation = 'move';
let clipboard = [];
let contextMenuTarget = null;

document.addEventListener('DOMContentLoaded', function() {
    if (!token) {
        showToast('GitHub token required to proceed', 'error');
        return;
    }
    checkTokenValidity();
    loadFileManager();
    setupEventListeners();
    setupContextMenu();
});

function setupEventListeners() {
    const dropzone = document.getElementById('dropzone');
    dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('active');
    });

    dropzone.addEventListener('dragleave', () => {
        dropzone.classList.remove('active');
    });

    dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.classList.remove('active');
        if (e.dataTransfer.files.length > 0) {
            document.getElementById('fileInput').files = e.dataTransfer.files;
            uploadFile();
        }
    });

    document.getElementById('browseFiles').addEventListener('click', () => {
        document.getElementById('fileInput').click();
    });

    document.getElementById('fileInput').addEventListener('change', () => {
        if (document.getElementById('fileInput').files.length > 0) {
            uploadFile();
        }
    });

    document.getElementById('copyLinkBtn').addEventListener('click', function() {
        const link = document.getElementById('uploadedFileLink').href;
        copyToClipboard(link);
        this.classList.add('clipboard-active');
        setTimeout(() => this.classList.remove('clipboard-active'), 2000);
    });

    document.getElementById('clipboardToggle').addEventListener('click', function(e) {
        e.stopPropagation();
        document.getElementById('clipboardMenu').classList.toggle('hidden');
    });

    document.addEventListener('click', function() {
        document.getElementById('clipboardMenu').classList.add('hidden');
    });
}

function setupContextMenu() {
    document.addEventListener('contextmenu', function(e) {
        e.preventDefault();
        const target = e.target.closest('.file-item, .folder-item');
        if (target) {
            contextMenuTarget = target.getAttribute('data-name');
            const menu = document.getElementById('contextMenu');
            menu.style.display = 'block';
            menu.style.left = `${e.pageX}px`;
            menu.style.top = `${e.pageY}px`;
        }
    });

    document.addEventListener('click', function() {
        document.getElementById('contextMenu').style.display = 'none';
    });
}

function contextMenuAction(action) {
    if (!contextMenuTarget) return;

    switch(action) {
        case 'open': openFile(contextMenuTarget); break;
        case 'copy': addToClipboard(contextMenuTarget, 'copy'); break;
        case 'cut': addToClipboard(contextMenuTarget, 'move'); break;
        case 'rename': renameFile(contextMenuTarget); break;
        case 'delete': deleteFile(contextMenuTarget); break;
    }
    contextMenuTarget = null;
}

function getFileIcon(fileName) {
    const extension = fileName.split('.').pop().toLowerCase();
    const audioExtensions = ['mp3', 'wav', 'ogg', 'm4a'];
    const imageExtensions = ['jpg', 'jpeg', 'png', 'gif', 'svg', 'webp'];
    const videoExtensions = ['mp4', 'avi', 'mov', 'mkv', 'webm'];
    const pdfExtensions = ['pdf'];
    const zipExtensions = ['zip', 'rar', '7z', 'tar', 'gz'];
    const codeExtensions = ['html', 'css', 'js', 'json', 'php', 'py', 'java', 'cpp', 'c', 'h'];
    const textExtensions = ['txt', 'md', 'csv'];

    if (audioExtensions.includes(extension)) return fileIcons.audio;
    if (imageExtensions.includes(extension)) return fileIcons.image;
    if (videoExtensions.includes(extension)) return fileIcons.video;
    if (pdfExtensions.includes(extension)) return fileIcons.pdf;
    if (zipExtensions.includes(extension)) return fileIcons.zip;
    if (codeExtensions.includes(extension)) return fileIcons.code;
    if (textExtensions.includes(extension)) return fileIcons.text;
    return fileIcons.default;
}

async function loadFileManager() {
    const fileList = document.getElementById('fileList');
    fileList.innerHTML = '';

    try {
        const repoPath = currentPath.slice(1).join('/') || '';
        const response = await fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${repoPath}`, {
            headers: {
                'Authorization': `token ${token}`,
                'Accept': 'application/vnd.github.v3+json'
            }
        });
        const data = await response.json();

        let currentFolder = fileSystem;
        for (let p of currentPath) {
            if (!currentFolder[p]) {
                currentFolder[p] = { type: 'folder', children: {} };
            }
            currentFolder = currentFolder[p].children;
        }

        Object.keys(currentFolder).forEach(key => delete currentFolder[key]);

        if (Array.isArray(data)) {
            data.forEach(item => {
                if (item.type === 'dir') {
                    currentFolder[item.name] = { type: 'folder', children: {} };
                } else if (item.type === 'file' && !item.name.endsWith('.gitkeep')) {
                    currentFolder[item.name] = { type: 'file', url: item.html_url };
                }
            });
            localStorage.setItem('fileSystem', JSON.stringify(fileSystem));
        }
    } catch (error) {
        showToast(`Error fetching repository: ${error.message}`, 'error');
        console.error('Error:', error);
    }

    updateBreadcrumbs();

    if (currentPath.length > 1) {
        const backItem = document.createElement('div');
        backItem.className = 'folder-item p-3 rounded-lg border border-gray-200 bg-gray-50 cursor-pointer flex items-center justify-center flex-col';
        backItem.innerHTML = `
            <div class="w-12 h-12 rounded-full bg-gray-100 flex items-center justify-center mb-2">
                <i class="fas fa-level-up-alt text-gray-500 text-xl"></i>
            </div>
            <span class="text-sm text-gray-700 text-center">Go Up</span>
        `;
        backItem.onclick = () => {
            currentPath.pop();
            loadFileManager();
        };
        fileList.appendChild(backItem);
    }

    Object.keys(currentFolder).forEach(name => {
        const item = currentFolder[name];
        if (item.type === 'folder') {
            fileList.appendChild(createFolderItem(name));
        }
    });

    Object.keys(currentFolder).forEach(name => {
        const item = currentFolder[name];
        if (item.type === 'file') {
            fileList.appendChild(createFileItem(name, item.url));
        }
    });
}

function createFolderItem(name) {
    const folderItem = document.createElement('div');
    folderItem.className = 'folder-item p-3 rounded-lg border border-gray-200 cursor-pointer hover:border-blue-300';
    folderItem.setAttribute('data-name', name);
    folderItem.innerHTML = `
        <div class="flex flex-col items-center">
            <div class="w-12 h-12 rounded-full bg-blue-50 flex items-center justify-center mb-2">
                <i class="fas fa-folder text-blue-400 text-2xl"></i>
            </div>
            <span class="text-sm text-gray-700 text-center truncate w-full">${name}</span>
        </div>
    `;

    folderItem.onclick = () => {
        currentPath.push(name);
        loadFileManager();
    };

    folderItem.draggable = true;
    folderItem.ondragstart = (e) => {
        draggedFile = name;
        folderItem.classList.add('dragging');
        e.dataTransfer.setData('text/plain', name);
        e.dataTransfer.effectAllowed = 'copyMove';
    };

    folderItem.ondragend = () => {
        folderItem.classList.remove('dragging');
        draggedFile = null;
    };

    folderItem.ondragover = (e) => {
        e.preventDefault();
        folderItem.classList.add('dragging-over');
        e.dataTransfer.dropEffect = dragOperation === 'copy' ? 'copy' : 'move';
    };

    folderItem.ondragleave = () => {
        folderItem.classList.remove('dragging-over');
    };

    folderItem.ondrop = (e) => {
        e.preventDefault();
        folderItem.classList.remove('dragging-over');
        const draggedName = e.dataTransfer.getData('text/plain');
        if (draggedName) {
            moveOrCopyFile(draggedName, name);
        }
    };

    return folderItem;
}

function createFileItem(name, url) {
    const fileItem = document.createElement('div');
    fileItem.className = 'file-item p-3 rounded-lg border border-gray-200 hover:border-blue-300 relative';
    fileItem.setAttribute('data-name', name);
    fileItem.innerHTML = `
        <div class="flex flex-col items-center">
            <div class="w-12 h-12 rounded-full bg-gray-50 flex items-center justify-center mb-2">
                <i class="${getFileIcon(name)} text-xl"></i>
            </div>
            <span class="text-sm text-gray-700 text-center truncate w-full">${name}</span>
            <div class="absolute top-1 right-1 flex space-x-1 opacity-0 hover:opacity-100 transition-opacity">
                <button class="p-1 text-gray-500 hover:text-blue-500" onclick="event.stopPropagation(); copyToClipboard('${url}')">
                    <i class="fas fa-copy text-xs"></i>
                </button>
                <button class="p-1 text-gray-500 hover:text-red-500" onclick="event.stopPropagation(); deleteFile('${name}')">
                    <i class="fas fa-trash-alt text-xs"></i>
                </button>
            </div>
        </div>
    `;

    fileItem.onclick = () => openFile(name);

    fileItem.draggable = true;
    fileItem.ondragstart = (e) => {
        draggedFile = name;
        fileItem.classList.add('dragging');
        e.dataTransfer.setData('text/plain', name);
        e.dataTransfer.effectAllowed = 'copyMove';
    };

    fileItem.ondragend = () => {
        fileItem.classList.remove('dragging');
        draggedFile = null;
    };

    return fileItem;
}

function updateBreadcrumbs() {
    const breadcrumbs = document.getElementById('breadcrumbs');
    breadcrumbs.innerHTML = '';

    currentPath.forEach((folder, index) => {
        const isLast = index === currentPath.length - 1;
        const crumb = document.createElement('span');
        crumb.className = 'flex items-center';

        if (!isLast) {
            const link = document.createElement('a');
            link.href = '#';
            link.className = 'text-blue-600 hover:underline whitespace-nowrap';
            link.textContent = folder;
            link.onclick = (e) => {
                e.preventDefault();
                currentPath = currentPath.slice(0, index + 1);
                loadFileManager();
            };
            crumb.appendChild(link);
            crumb.innerHTML += '<span class="mx-2 text-gray-400">/</span>';
        } else {
            const span = document.createElement('span');
            span.className = 'font-medium text-gray-800 whitespace-nowrap';
            span.textContent = folder;
            crumb.appendChild(span);
        }

        breadcrumbs.appendChild(crumb);
    });
}

function navigateToRoot() {
    currentPath = ['root'];
    loadFileManager();
}

function setDragOperation(operation) {
    dragOperation = operation;
    showToast(`Clipboard mode set to <strong>${operation}</strong>`);
    updateClipboardStatus();
}

async function moveOrCopyFile(fileName, targetFolder) {
    let currentFolder = fileSystem;
    let sourcePath = [...currentPath];
    for (let p of currentPath) {
        currentFolder = currentFolder[p].children;
    }

    let sourceItem = currentFolder[fileName];
    if (!sourceItem) {
        const clipboardItem = clipboard.find(item => item.name === fileName);
        if (!clipboardItem) {
            showToast('Source item not found', 'error');
            return;
        }
        fileName = clipboardItem.name;
        sourcePath = clipboardItem.path;
        currentFolder = fileSystem;
        for (let p of clipboardItem.path) {
            currentFolder = currentFolder[p].children;
        }
        sourceItem = currentFolder[fileName];
    }

    if (!sourceItem) {
        showToast('Source item not found', 'error');
        return;
    }

    let target = fileSystem;
    for (let p of [...currentPath, targetFolder]) {
        target = target[p].children;
    }

    if (target[fileName]) {
        showToast('Item already exists in target folder', 'error');
        return;
    }

    const sourceRepoPath = [...sourcePath.slice(1), fileName].join('/');
    const targetRepoPath = [...currentPath.slice(1), targetFolder, fileName].join('/');

    try {
        if (sourceItem.type === 'file') {
            if (dragOperation === 'move') {
                await deleteFileFromGitHub(sourceRepoPath);
                await copyFileToGitHub(sourceItem.url, targetRepoPath);
                delete currentFolder[fileName];
            } else {
                await copyFileToGitHub(sourceItem.url, targetRepoPath);
            }
            target[fileName] = { ...sourceItem, url: `https://github.com/${repoOwner}/${repoName}/blob/main/${targetRepoPath}` };
        } else {
            if (dragOperation === 'move') {
                await moveFolderInGitHub(sourceRepoPath, targetRepoPath);
                delete currentFolder[fileName];
            } else {
                await copyFolderInGitHub(sourcePath, targetRepoPath);
            }
            target[fileName] = { ...sourceItem };
        }

        if (dragOperation === 'move') {
            clipboard = clipboard.filter(item => item.name !== fileName);
            updateClipboardStatus();
        }
        localStorage.setItem('fileSystem', JSON.stringify(fileSystem));
        await loadFileManager();
        showToast(`Item ${dragOperation === 'move' ? 'moved' : 'copied'} successfully`);
    } catch (error) {
        showToast(`Failed to ${dragOperation} item: ${error.message}`, 'error');
        console.error('Error:', error);
    }
}

function createFolderPrompt() {
    const folderName = prompt('Enter folder name:');
    if (folderName) createFolder(folderName);
}

async function createFolder(folderName) {
    if (!folderName.trim()) {
        showToast('Please enter a folder name', 'error');
        return;
    }

    let currentFolder = fileSystem;
    for (let p of currentPath) {
        currentFolder = currentFolder[p].children;
    }

    if (currentFolder[folderName]) {
        showToast('Folder already exists', 'error');
        return;
    }

    const folderPath = [...currentPath.slice(1), folderName].filter(p => p !== 'root').join('/');
    const gitHubPath = folderPath ? `${folderPath}/.gitkeep` : `${folderName}/.gitkeep`;

    try {
        await fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${gitHubPath}`, {
            method: 'PUT',
            headers: {
                'Authorization': `token ${token}`,
                'Accept': 'application/vnd.github.v3+json'
            },
            body: JSON.stringify({
                message: `Create folder ${folderName}`,
                content: btoa('')
            })
        });

        currentFolder[folderName] = { type: 'folder', children: {} };
        localStorage.setItem('fileSystem', JSON.stringify(fileSystem));
        await loadFileManager();
        showToast(`Folder "${folderName}" created`);
    } catch (error) {
        showToast(`Error creating folder: ${error.message}`, 'error');
        console.error('Error:', error);
    }
}

function openFile(fileName) {
    let currentFolder = fileSystem;
    for (let p of currentPath) {
        currentFolder = currentFolder[p].children;
    }

    if (currentFolder[fileName] && currentFolder[fileName].url) {
        window.open(currentFolder[fileName].url, '_blank');
    }
}

async function renameFile(fileName) {
    const newName = prompt('Enter new name:', fileName);
    if (!newName || newName === fileName) return;

    let currentFolder = fileSystem;
    for (let p of currentPath) {
        currentFolder = currentFolder[p].children;
    }

    if (currentFolder[newName]) {
        showToast('An item with that name already exists', 'error');
        return;
    }

    const sourceRepoPath = [...currentPath.slice(1), fileName].join('/');
    const targetRepoPath = [...currentPath.slice(1), newName].join('/');

    try {
        const sourceItem = currentFolder[fileName];
        if (sourceItem.type === 'file') {
            await deleteFileFromGitHub(sourceRepoPath);
            await copyFileToGitHub(sourceItem.url, targetRepoPath);
            currentFolder[newName] = { ...sourceItem, url: `https://github.com/${repoOwner}/${repoName}/blob/main/${targetRepoPath}` };
        } else {
            await moveFolderInGitHub(sourceRepoPath, targetRepoPath);
            currentFolder[newName] = { ...sourceItem };
        }

        delete currentFolder[fileName];
        localStorage.setItem('fileSystem', JSON.stringify(fileSystem));
        await loadFileManager();
        showToast(`Item renamed to "${newName}"`);
    } catch (error) {
        showToast(`Error renaming item: ${error.message}`, 'error');
        console.error('Error:', error);
    }
}

function saveFile(fileName, fileUrl) {
    let currentFolder = fileSystem;
    for (let p of currentPath) {
        currentFolder = currentFolder[p].children;
    }

    currentFolder[fileName] = { type: 'file', url: fileUrl };
    localStorage.setItem('fileSystem', JSON.stringify(fileSystem));
    loadFileManager();
}

async function deleteFile(fileName) {
    if (!confirm(`Are you sure you want to delete "${fileName}"?`)) return;

    let currentFolder = fileSystem;
    for (let p of currentPath) {
        currentFolder = currentFolder[p].children;
    }

    const repoPath = [...currentPath.slice(1), fileName].join('/');

    try {
        if (currentFolder[fileName].type === 'file') {
            await deleteFileFromGitHub(repoPath);
        } else {
            await deleteFolderFromGitHub(repoPath);
        }
        delete currentFolder[fileName];
        localStorage.setItem('fileSystem', JSON.stringify(fileSystem));
        await loadFileManager();
        showToast(`"${fileName}" deleted`);
    } catch (error) {
        showToast(`Error deleting item: ${error.message}`, 'error');
        console.error('Error:', error);
    }
}

function addToClipboard(fileName, operation) {
    clipboard = clipboard.filter(item => item.name !== fileName);
    clipboard.push({ name: fileName, path: [...currentPath], operation });
    updateClipboardStatus();
    showToast(`"${fileName}" added to clipboard (${operation} mode)`);
}

function pasteFromClipboard() {
    if (clipboard.length === 0) {
        showToast('Clipboard is empty', 'error');
        return;
    }

    clipboard.forEach(item => {
        dragOperation = item.operation;
        moveOrCopyFile(item.name, currentPath[currentPath.length - 1]);
    });

    if (dragOperation === 'move') {
        clipboard = [];
        updateClipboardStatus();
    }
}

function clearClipboard() {
    clipboard = [];
    updateClipboardStatus();
    showToast('Clipboard cleared');
}

function updateClipboardStatus() {
    const clipboardStatus = document.getElementById('clipboardStatus');
    if (clipboard.length === 0) {
        clipboardStatus.innerHTML = '<i class="far fa-clipboard mr-1"></i> Empty';
        return;
    }

    const operations = clipboard.reduce((acc, item) => {
        acc[item.operation] = (acc[item.operation] || 0) + 1;
        return acc;
    }, {});

    let statusText = '';
    if (operations.copy) statusText += `<span class="text-blue-600">${operations.copy} copy</span>`;
    if (operations.move) {
        if (operations.copy) statusText += ', ';
        statusText += `<span class="text-green-600">${operations.move} cut</span>`;
    }

    clipboardStatus.innerHTML = `<i class="far fa-clipboard mr-1"></i> ${statusText}`;
}

function checkTokenValidity() {
    fetchWithRateLimit('https://api.github.com/user', {
        headers: {
            'Authorization': `token ${token}`,
            'Accept': 'application/vnd.github.v3+json'
        }
    })
    .then(res => res.json())
    .then(data => {
        const statusMessage = document.getElementById('statusMessage');
        if (data.login) {
            statusMessage.innerHTML = `<i class="fas fa-check-circle mr-2 text-green-500"></i> Connected as: <strong>${data.login}</strong>`;
            statusMessage.className = 'p-3 rounded-lg bg-green-50 text-green-800 flex items-center';
            checkFolderExistence();
        } else {
            statusMessage.innerHTML = `<i class="fas fa-times-circle mr-2 text-red-500"></i> Invalid GitHub token`;
            statusMessage.className = 'p-3 rounded-lg bg-red-50 text-red-800 flex items-center';
        }
    })
    .catch(error => {
        const statusMessage = document.getElementById('statusMessage');
        statusMessage.innerHTML = `<i class="fas fa-times-circle mr-2 text-red-500"></i> Error connecting to GitHub: ${error.message}`;
        statusMessage.className = 'p-3 rounded-lg bg-red-50 text-red-800 flex items-center';
    });
}

function checkFolderExistence() {
    fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/`, {
        headers: {
            'Authorization': `token ${token}`
        }
    })
    .then(res => res.json())
    .then(data => {
        const folderStatus = document.getElementById('folderStatus');
        const folder = Array.isArray(data) ? data.find(item => item.type === "dir" && item.name === "videos") : null;
        if (folder) {
            folderStatus.innerHTML = `<i class="fas fa-check-circle mr-2 text-green-500"></i> Folder 'videos' found in repository`;
            folderStatus.className = 'p-3 rounded-lg bg-green-50 text-green-800 flex items-center';
        } else {
            folderStatus.innerHTML = `<i class="fas fa-exclamation-triangle mr-2 text-yellow-500"></i> Folder 'videos' not found (files will upload to root)`;
            folderStatus.className = 'p-3 rounded-lg bg-yellow-50 text-yellow-800 flex items-center';
        }
    })
    .catch(error => {
        const folderStatus = document.getElementById('folderStatus');
        folderStatus.innerHTML = `<i class="fas fa-exclamation-triangle mr-2 text-yellow-500"></i> Error checking repository: ${error.message}`;
        folderStatus.className = 'p-3 rounded-lg bg-yellow-50 text-yellow-800 flex items-center';
    });
}

async function uploadFile() {
    const file = document.getElementById('fileInput').files[0];
    const message = document.getElementById('message');
    const uploadProgress = document.getElementById('uploadProgress');
    const progressBar = document.getElementById('progressBar');
    const progressPercent = document.getElementById('progressPercent');
    const uploadedSize = document.getElementById('uploadedSize');
    const estimatedTime = document.getElementById('estimatedTime');
    const fileLink = document.getElementById('fileLink');

    if (!file) {
        showToast('Please select a file first', 'error');
        return;
    }

    message.className = 'hidden';
    fileLink.className = 'hidden';
    uploadProgress.classList.remove('hidden');
    progressBar.style.width = '0%';
    progressPercent.textContent = '0%';
    uploadedSize.textContent = '0 MB';
    estimatedTime.textContent = 'Estimating time...';

    const path = [...currentPath.slice(1), file.name].filter(p => p !== 'root').join('/') || file.name;
    const url = `https://api.github.com/repos/${repoOwner}/${repoName}/contents/${path}`;

    const reader = new FileReader();
    reader.onloadend = async function() {
        const base64 = reader.result.split(',')[1];
        const data = {
            message: `Upload ${file.name}`,
            content: base64
        };

        let progress = 0;
        const fileSizeMB = file.size / (1024 * 1024);
        const uploadSpeedMBps = 2;
        const estimatedSeconds = Math.ceil(fileSizeMB / uploadSpeedMBps);
        estimatedTime.textContent = `~${estimatedSeconds} sec remaining`;

        const progressInterval = setInterval(() => {
            progress += Math.random() * 5;
            if (progress > 90) progress = 90;
            progressBar.style.width = `${progress}%`;
            progressPercent.textContent = `${Math.round(progress)}%`;
            uploadedSize.textContent = `${(fileSizeMB * progress / 100).toFixed(1)} MB / ${fileSizeMB.toFixed(1)} MB`;
        }, 500);

        try {
            const response = await fetchWithRateLimit(url, {
                method: 'PUT',
                headers: {
                    'Authorization': `token ${token}`,
                    'Accept': 'application/vnd.github.v3+json'
                },
                body: JSON.stringify(data)
            });
            const result = await response.json();

            clearInterval(progressInterval);
            progressBar.style.width = '100%';
            progressPercent.textContent = '100%';
            uploadedSize.textContent = `${fileSizeMB.toFixed(1)} MB / ${fileSizeMB.toFixed(1)} MB`;
            estimatedTime.textContent = 'Upload complete!';

            setTimeout(() => {
                uploadProgress.classList.add('hidden');
                if (result.content) {
                    showToast('Upload successful!', 'success');
                    const fileUrl = result.content.html_url;
                    saveFile(file.name, fileUrl);
                    document.getElementById('uploadedFileLink').textContent = fileUrl;
                    document.getElementById('uploadedFileLink').href = fileUrl;
                    fileLink.classList.remove('hidden');
                } else {
                    showToast('Upload failed', 'error');
                }
            }, 1000);
        } catch (error) {
            clearInterval(progressInterval);
            uploadProgress.classList.add('hidden');
            showToast(`Upload failed: ${error.message}`, 'error');
        }
    };
    reader.readAsDataURL(file);
}

async function copyFileToGitHub(sourceUrl, targetPath) {
    const sourceResponse = await fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${sourceUrl.split('/blob/main/')[1]}`, {
        headers: {
            'Authorization': `token ${token}`,
            'Accept': 'application/vnd.github.v3+json'
        }
    });
    const sourceData = await sourceResponse.json();
    if (!sourceData.content) throw new Error('Failed to fetch file content');

    const data = {
        message: `Copy file to ${targetPath}`,
        content: sourceData.content
    };

    const response = await fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${targetPath}`, {
        method: 'PUT',
        headers: {
            'Authorization': `token ${token}`,
            'Accept': 'application/vnd.github.v3+json'
        },
        body: JSON.stringify(data)
    });

    if (!response.ok) {
        const errorData = await response.json();
        throw new Error(`Failed to copy file: ${errorData.message}`);
    }
}

async function copyFolderInGitHub(sourcePath, targetPath) {
    const response = await fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${sourcePath}`, {
        headers: {
            'Authorization': `token ${token}`,
            'Accept': 'application/vnd.github.v3+json'
        }
    });
    const contents = await response.json();

    if (!Array.isArray(contents)) throw new Error('Failed to fetch folder contents');

    for (const item of contents) {
        const newSourcePath = `${sourcePath}/${item.name}`;
        const newTargetPath = `${targetPath}/${item.name}`;
        if (item.type === 'file' && !item.name.endsWith('.gitkeep')) {
            await copyFileToGitHub(item.html_url, newTargetPath);
        } else if (item.type === 'dir') {
            await copyFolderInGitHub(newSourcePath, newTargetPath);
        }
        await delay(100); // Prevent rate limit issues
    }

    const gitkeepPath = `${targetPath}/.gitkeep`;
    await fetchWithRateLimit(`https://api.github.com/repos/${repoOwner}/${repoName}/contents/${gitkeepPath}`, {
        method: 'PUT',
        headers: {
            'Authorization': `token ${token}`,
            'Accept': 'application/vnd.github.v3+json'
        },
        body: JSON.stringify({
            message: `Create .gitkeep for ${target    function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(() => {
        showToast('Copied to clipboard!');
    }).catch(() => {
        showToast('Failed to copy to clipboard', 'error');
    });
}

// Show toast notification
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    toast.innerHTML = message;

    toast.style.background = type === 'error' ? '#ef4444' : type === 'success' ? '#10b981' : '#3b82f6';
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 3000);
}

// Rate limit handler
async function fetchWithRateLimit(url, options) {
    const response = await fetch(url, options);
    if (response.status === 403 || response.status === 429) {
        const retryAfter = response.headers.get('Retry-After') || 10;
        showToast(`Rate limit exceeded, retrying after ${retryAfter}s`, 'error');
        await delay(retryAfter * 1000);
        return fetchWithRateLimit(url, options);
    }
    if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'API request failed');
    }
    return response;
}

// Delay function for rate limiting
function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}