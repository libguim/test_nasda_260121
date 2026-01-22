(function() {

    let categories = [];
    let stickersInPalette = [];
    let stickers = []; // 현재 화면의 스티커 상태 (메모리)
    let stickerBackup = [];
    let isDecorating = false;
    let selectedSticker = null;

    // ==========================================
    //  [AUTHORIZATION] 권한 및 유틸리티
    // ==========================================

    function hasPermission(sticker) {
        const currentId = String(window.ST_DATA?.currentUserId || '').trim();
        const ownerId = String(window.ST_DATA?.postOwnerId || '').trim();
        const authorId = String(sticker.authorLoginId || '').trim();

        // 💡 디버깅 로그 추가
        console.group(`🔍 권한 체크 (스티커 ID: ${sticker.dbId || '신규'})`);
        console.log(`- 현재 로그인 유저: [${currentId}]`);
        console.log(`- 게시글 주인: [${ownerId}]`);
        console.log(`- 스티커 작성자: [${authorId}]`);

        if (!currentId || currentId === 'anonymous') {
            console.warn("❌ 결과: 로그인하지 않은 사용자 (권한 없음)");
            console.groupEnd();
            return false;
        }

        if (currentId === ownerId) {
            console.info("✅ 결과: 게시글 주인 권한 승인");
            console.groupEnd();
            return true;
        }

        if (currentId === authorId) {
            console.info("✅ 결과: 스티커 작성자 권한 승인");
            console.groupEnd();
            return true;
        }

        console.error("❌ 결과: 권한 불일치 (조작 불가)");
        console.groupEnd();
        return false;
    }

    function validateDeletePermission(sticker) {
        if (hasPermission(sticker)) return true;
        throw new Error("🔒 이 스티커를 삭제할 권한이 없습니다.");
    }

    async function fetchStickerCategories() {
        try {
            const response = await axios.get('/api/sticker-categories');
            categories = response.data;
            renderCategoryTabs();
            if (categories.length > 0) fetchStickersByCategory(categories[0].stickerCategoryId);
        } catch (err) { console.error("카테고리 로드 실패"); }
    }

    async function fetchStickersByCategory(categoryId) {
        try {
            const response = await axios.get(`/api/stickers/categories/${categoryId}`);
            stickersInPalette = response.data;
            renderPalette();
        } catch (err) { console.error("스티커 로드 실패"); }
    }

    // 팔레트 및 카테고리 로직 (기존 유지)
    window.startDecoration = async function() {
        isDecorating = true;

        stickerBackup = JSON.parse(JSON.stringify(stickers));
        console.log("💾 취소에 대비해 현재 스티커 상태를 백업했습니다.");

        document.querySelectorAll('.sticker-layer').forEach(l => l.style.pointerEvents = 'auto');
        document.getElementById('deco-active-view')?.classList.remove('hidden');
        document.getElementById('deco-start-view')?.classList.add('hidden');
        await fetchStickerCategories();
    };

    // 꾸미기를 취소하고 원래 상태로 되돌리는 함수
    window.cancelDecoration = async function() {
        if (confirm("변경 사항을 저장하지 않고 취소하시겠습니까?")) {

            stickers = JSON.parse(JSON.stringify(stickerBackup));

            // 1. 선택 해제 및 화면 다시 그리기 (저장 전 상태로 복구)

            selectedSticker = null;
            isDecorating = false;

            console.log("🔄 모든 변경사항을 취소하고 원본으로 복구했습니다.");

            await renderStickers();

            // 2. UI 닫기
            document.querySelectorAll('.sticker-layer').forEach(l => l.style.pointerEvents = 'none');
            document.getElementById('deco-active-view')?.classList.add('hidden');
            document.getElementById('deco-start-view')?.classList.remove('hidden');

            if (window.mySwiper) window.mySwiper.allowTouchMove = true;
            console.log("🎨 스티커 붙이기가 취소되었습니다.");
        }
    };

    // ==========================================
    //  [DELETE] 일괄 삭제 (시나리오 A & B)
    // ==========================================

    window.clearAllStickers = async function() {
        const currentId = String(window.ST_DATA?.currentUserId || '').trim();
        const ownerId = String(window.ST_DATA?.postOwnerId || '').trim();

        if (!currentId || currentId === 'anonymous') {
            alert("🔒 로그인이 필요합니다.");
            return;
        }

        // 시나리오 A: 내가 주인인 게시글 -> 전체 삭제
        if (currentId === ownerId) {
            if (confirm('모든 스티커를 지우시겠습니까?')) {
                try {
                    // 💡 핀셋 3: 서버의 모든 스티커 삭제 API가 있다면 호출, 없다면 개별 삭제 반복
                    // 현재 백엔드 로직에 맞춰 stickers 배열의 모든 dbId를 처리하거나
                    // 특정 이미지의 전체 삭제 API를 호출해야 합니다.
                    stickers = [];
                    selectedSticker = null;
                    await renderStickers(); // 화면 즉시 비움
                    console.log("⚠️ 안내: 화면에서 모든 스티커가 제거되었습니다. [저장하기]를 눌러야 DB에 반영됩니다.");
                } catch (err) { alert("삭제 중 오류 발생"); }
            }
        }
        // 시나리오 B: 남의 게시글 -> 본인 것만 삭제
        else {
            if (confirm('본인의 스티커만 지우시겠습니까?')) {
                stickers = stickers.filter(s => String(s.authorLoginId).trim() !== currentId);
                selectedSticker = null;
                await renderStickers(); // 💡 화면 갱신

                console.log("⚠️ 안내: 본인의 스티커가 화면에서 제거되었습니다. [저장하기]를 눌러야 DB에 반영됩니다.");
            }
        }
    };

    // ==========================================
    //  [SAVE] 최종 저장 (내가 작성한 것만 업데이트)
    // ==========================================

    window.saveDecoration = async function() {
        const currentUserId = window.ST_DATA?.currentUserId;
        const rawUserId = window.ST_DATA?.rawUserId;
        const postId = window.ST_DATA?.postId;

        if (!currentUserId || currentUserId === 'anonymous') {
            alert("🔒 로그인 후 저장할 수 있습니다.");
            return;
        }

        const hasDeletedOrAdded = stickers.some(s => !s.dbId) ||
            (window.INITIAL_STICKER_COUNT !== stickers.length);
        const hasModified = stickers.some(s => s.isDirty === true);

        if (!hasDeletedOrAdded && !hasModified) {
            alert("변경 사항이 없습니다.");

            // UI 즉시 정리 및 종료
            isDecorating = false;
            selectedSticker = null;
            document.querySelectorAll('.sticker-layer').forEach(l => l.style.pointerEvents = 'none');
            document.getElementById('deco-active-view')?.classList.add('hidden');
            document.getElementById('deco-start-view')?.classList.remove('hidden');
            if (window.mySwiper) window.mySwiper.allowTouchMove = true;

            console.log("🍃 변경 사항이 없어 패널을 닫습니다.");
            return;
        }

        // 1. 화면의 모든 이미지 레이어를 찾음
        const allImageLayers = Array.from(document.querySelectorAll('.sticker-layer'));
        const ownerId = String(window.ST_DATA?.postOwnerId || '').trim();
        const currentId = String(window.ST_DATA?.currentUserId || '').trim();

        // 2. 각 레이어(이미지)별로 저장 요청 생성
        const savePromises = allImageLayers.map(layer => {
            const imageId = Number(layer.getAttribute('data-image-id'));

            let stickersToSave;

            if (currentId === ownerId) {
                // 시나리오 A: 내가 주인인 경우 -> 이 이미지에 붙은 '모든' 스티커를 보냅니다.
                // (모두 지우기를 했다면 stickers가 빈 배열이므로, 서버에 빈 배열이 전달되어 DB가 비워집니다.)
                stickersToSave = stickers.filter(s => s.postImageId === imageId);
            } else {
                // 시나리오 B: 내가 방문자인 경우 -> 오직 '내'가 붙인 스티커만 보냅니다.
                stickersToSave = stickers.filter(s =>
                    s.postImageId === imageId && s.authorLoginId === currentId
                );
            }

            console.log(`📡 이미지(${imageId}) 저장 대상 수: ${stickersToSave.length}개`);

            // 해당 이미지에 내가 붙인 스티커가 하나도 없더라도
            // 서버에서 '전체 삭제 후 갱신' 처리를 한다면 빈 배열을 보내야 할 수도 있습니다.
            // 여기서는 안전하게 내가 관리하는 스티커들만 보냅니다.
            return axios.post('/api/decorations', {
                postImageId: imageId,
                userId: rawUserId,
                decorations: stickersToSave.map(s => ({
                    // 기존 스티커라면 dbId(decorationId)가 있고, 새로 만든 거라면 없습니다.
                    decorationId: s.dbId || null,
                    stickerId: s.stickerId,
                    posX: parseFloat((Number(s.x ?? s.originX) || 0).toFixed(2)),
                    posY: parseFloat((Number(s.y ?? s.originY) || 0).toFixed(2)),
                    scale: parseFloat((Number(s.scale) || 1.0).toFixed(2)),
                    rotation: s.rotation || 0,
                    zIndex: 10
                }))
            });
        });

        // 3. 모든 레이어의 저장 요청이 완료될 때까지 대기
        // [SAVE] 최종 저장 로직 수정
        try {
            console.log("⏳ 1. 저장 요청 시작...");
            await Promise.all(savePromises);
            console.log("✅ 2. 모든 이미지 저장 완료");

            const response = await axios.get(`/api/decorations/post/${postId}`);
            const allUpdatedStickers = response.data;

            // 디버깅 콘솔 생성
            console.group("📊 데이터 동기화 디버깅");
            console.log("- 서버 전체 응답 데이터:", allUpdatedStickers);
            console.log("- 데이터 타입:", Array.isArray(allUpdatedStickers) ? "Array" : typeof allUpdatedStickers);
            console.log("- 데이터 개수:", allUpdatedStickers?.length);
            console.groupEnd();

            if (!allUpdatedStickers) {
                throw new Error("서버에서 받은 스티커 데이터가 비어 있습니다.");
            }

            // 💡 [개선] 데이터가 있든 없든 항상 stickers 배열을 서버 데이터로 동기화합니다.
            stickers = allUpdatedStickers.map(item => ({
                dbId: item.decorationId,
                postImageId: item.postImageId,
                stickerId: item.stickerId,
                imgUrl: item.stickerImageUrl,
                x: item.posX,
                y: item.posY,
                originX: item.posX,
                originY: item.posY,
                scale: item.scale || 1.0,
                rotation: item.rotation || 0,
                authorLoginId: String(item.loginId || '').trim(),
                authorNickname: item.nickname || "사용자"
            }));

            window.INITIAL_STICKER_COUNT = stickers.length;
            alert("스티커 설정이 저장되었습니다! ✨");

            selectedSticker = null;
            await renderStickers();

            isDecorating = false; // 꾸미기 모드 종료

            // 스티커 레이어의 클릭/드래그 막기
            document.querySelectorAll('.sticker-layer').forEach(l => l.style.pointerEvents = 'auto');

            // 패널 숨기고 시작 버튼 보여주기
            document.getElementById('deco-active-view')?.classList.add('hidden'); // 팔레트 닫기
            document.getElementById('deco-start-view')?.classList.remove('hidden'); // 시작 버튼 보이기
            if (window.mySwiper) window.mySwiper.allowTouchMove = true;

        } catch (err) {
            console.error("저장 실패 상세 로직:", err);
            const errorMsg = err.response?.data?.message || err.message;
            alert("저장 중 오류가 발생했습니다: " + errorMsg);
        }
    };

    // ==========================================
    //  [READ & RENDER] 데이터 조회 및 화면 렌더링
    // ==========================================

    async function renderStickers() {
        // 💡 렌더링 전 모든 레이어를 깨끗이 비워 중복 렌더링을 방지합니다.
        document.querySelectorAll('.sticker-layer').forEach(layer => {
            layer.innerHTML = '';
        });

        // 💡 핀셋 수정: forEach 대신 for...of를 사용하여 비동기 흐름이 무시되지 않도록 합니다.
        for (const s of stickers) {
            const targetLayer = document.querySelector(`.sticker-layer[data-image-id="${s.postImageId}"]`);
            if (!targetLayer) continue;

            const isSelected = (selectedSticker === s);
            const canManage = hasPermission(s); //
            const absoluteSize = 92 * (s.scale || 1);

            // 툴팁 이름 결정
            const authorDisplayName = s.authorNickname || "사용자";

            const el = document.createElement('div');
            const cursorClass = isDecorating && canManage ? 'cursor-move' : 'cursor-default';
            el.className = `sticker-item absolute transform -translate-x-1/2 -translate-y-1/2 group ${cursorClass} ${isSelected ? 'z-[10000]' : 'z-10'}`;

            el.style.width = absoluteSize + 'px';
            el.style.height = absoluteSize + 'px';
            el.style.left = s.x + '%';
            el.style.top = s.y + '%';
            el.style.transform = `translate(-50%, -50%) rotate(${s.rotation || 0}deg)`;
            el.setAttribute('title', `✨ ${authorDisplayName}님이 붙였어요`);

            // 기존 레이아웃 구조 절대 유지
            let innerContent = `<img src="${s.imgUrl}" class="sticker-main-img" style="width:100%; height:100%; display:block; pointer-events:none; object-fit:contain; ${isSelected && canManage ? 'filter: drop-shadow(0 0 10px #fbcfe8); border: 2.5px dashed #fbcfe8; border-radius: 12px;' : ''}">`;

            // 권한이 있는 경우에만 삭제 버튼과 조작 패널 렌더링
            if (canManage) {
                innerContent += `
                <div class="btn-single-remove ${isSelected ? '' : 'hidden'}" style="position: absolute; top: -12px; right: -12px; width: 28px; height: 28px; background-color: #ff4d4f; color: white; border: 2px solid white; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: bold; cursor: pointer; z-index: 10010;">×</div>
                <div class="sticker-control-panel absolute -bottom-16 left-1/2 -translate-x-1/2 flex gap-1.5 bg-white/95 p-2 rounded-full shadow-2xl border border-pink-200 z-[10001] ${isSelected ? '' : 'hidden'}" style="min-width: 150px;">
                    <button type="button" class="c-btn op-up">➕</button>
                    <button type="button" class="c-btn op-down">➖</button>
                    <button type="button" class="c-btn op-rotate">🔄</button>
                    <button type="button" class="c-btn op-reset">🧹</button>
                </div>`;
            }

            el.innerHTML = innerContent;

            // --- 이벤트 바인딩 (Delete & Update) ---
            if (isSelected && canManage) {
                el.querySelector('.btn-single-remove')?.addEventListener('click', async (e) => {
                    e.stopPropagation();

                    if (confirm("스티커를 삭제하시겠습니까?")) {
                        // 💡 디버깅 로그: 삭제될 스티커 정보 출력
                        console.group("🗑️ 스티커 삭제 예약");
                        console.log(`- 삭제 대상 dbId: ${s.dbId || '신규 스티커(DB에 아직 없음)'}`);
                        console.log(`- 현재 메모리 스티커 수: ${stickers.length}개`);

                        // 1. 메모리(stickers 배열)에서 제외
                        stickers = stickers.filter(item => item !== s);

                        // 2. 선택 상태 해제
                        selectedSticker = null;

                        // 3. 화면 재렌더링
                        await renderStickers();

                        console.log(`- 삭제 후 메모리 스티커 수: ${stickers.length}개`);
                        console.log("⚠️ 안내: [저장하기] 버튼을 눌러야 DB에 최종 반영됩니다.");
                        console.groupEnd();
                    }
                });

                // 조작 버튼 연동
                el.querySelectorAll('.c-btn').forEach(btn => {
                    btn.onclick = async (e) => {
                        e.stopPropagation();
                        const op = btn.classList.contains('op-up') ? 'scale' :
                            btn.classList.contains('op-down') ? 'scale' :
                                btn.classList.contains('op-rotate') ? 'rotate' : 'reset';
                        const val = btn.classList.contains('op-up') ? 0.1 :
                            btn.classList.contains('op-down') ? -0.1 :
                                btn.classList.contains('op-rotate') ? 15 : 0;
                        await updateAction(op, val);
                        await renderStickers();
                    };
                });
            }

            // 드래그 로직 (Update)
            el.onmousedown = async (e) => {

                if (!isDecorating) return;
                if (e.target.closest('.sticker-control-panel') || e.target.classList.contains('btn-single-remove')) return;

                e.preventDefault();
                e.stopPropagation();

                // 💡 클릭 시점 로그
                const canManage = hasPermission(s);
                console.log(`🖱️ 스티커 클릭됨 - 조작 가능 여부: ${canManage}`);

                if (!canManage) return;

                // 💡 스티커 조작이 시작되면 Swiper 슬라이드 기능을 잠급니다.
                if (window.mySwiper) {
                    window.mySwiper.allowTouchMove = false; // 터치/마우스 이동 금지
                }

                if (selectedSticker !== s) {
                    selectedSticker = s;
                    await renderStickers(); // 선택 상태 변경 렌더링
                    return;
                }

                const rect = targetLayer.getBoundingClientRect();
                const onMouseMove = (mE) => {
                    const newX = Math.max(0, Math.min(100, ((mE.clientX - rect.left) / rect.width) * 100));
                    const newY = Math.max(0, Math.min(100, ((mE.clientY - rect.top) / rect.height) * 100));

                    // 💡 소수점 단위 미세한 차이로 인한 dirty 방지를 위해 간단한 비교
                    if (Math.abs(s.x - newX) > 0.01 || Math.abs(s.y - newY) > 0.01) {
                        hasMoved = true;
                    }

                    s.x = newX;
                    s.y = newY;
                    el.style.left = s.x + '%';
                    el.style.top = s.y + '%';
                };
                const onMouseUp = async () => { // 💡 async 추가
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);

                    // 💡 드래그가 끝나면 다시 Swiper 슬라이드 기능을 켭니다.
                    if (window.mySwiper) {
                        window.mySwiper.allowTouchMove = true;
                    }

                    if (hasMoved) {
                        s.isDirty = true;
                        console.log(`📍 스티커(${s.dbId || '신규'})가 이동되었습니다. (Dirty: true)`);
                    }

                };
                document.addEventListener('mousemove', onMouseMove);
                document.addEventListener('mouseup', onMouseUp);
            };

            targetLayer.appendChild(el);
        }
    }

    function renderCategoryTabs() {
        const tabContainer = document.getElementById('sticker-category-tabs');
        if (!tabContainer) return;
        tabContainer.innerHTML = '';
        categories.forEach((cat, idx) => {
            const tab = document.createElement('button');
            tab.className = `category-btn ${idx === 0 ? 'active' : ''}`;
            tab.textContent = cat.name;
            tab.onclick = () => {
                document.querySelectorAll('.category-btn').forEach(b => b.classList.remove('active'));
                tab.classList.add('active');
                fetchStickersByCategory(cat.stickerCategoryId);
            };
            tabContainer.appendChild(tab);
        });
    }

    function renderPalette() {
        const palette = document.getElementById('sticker-palette');
        if (!palette) return;
        palette.innerHTML = '';
        stickersInPalette.forEach((sticker) => {
            const div = document.createElement('div');
            div.className = 'palette-item cursor-grab p-2 hover:bg-pink-50 rounded-xl flex items-center justify-center bg-transparent';
            div.innerHTML = `<img src="${sticker.stickerImageUrl}" onerror="this.remove()" class="w-12 h-12 object-contain pointer-events-none bg-transparent">`;
            div.draggable = true;
            div.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('imgUrl', sticker.stickerImageUrl);
                e.dataTransfer.setData('stickerId', sticker.stickerId);
            });
            palette.appendChild(div);
        });
    }

    // ==========================================
    //  [CREATE & UPDATE] 추가 및 조작 이벤트
    // ==========================================

    async function updateAction(type, val) {
        if (!selectedSticker) return;

        if (type === 'scale') {
            const scaleStep = 10 / 92;
            selectedSticker.scale = Math.max(0.4, (selectedSticker.scale || 1.0) + (val > 0 ? scaleStep : -scaleStep));
        } else if (type === 'rotate') {
            selectedSticker.rotation = ((selectedSticker.rotation || 0) + val) % 360;
        } else if (type === 'reset') {
            selectedSticker.scale = 1.0; selectedSticker.rotation = 0;
        }
        // 💡 변경됨을 표시
        selectedSticker.isDirty = true;
        await renderStickers();

        if (selectedSticker.dbId) {
            try {
                await axios.put(`/api/decorations/${selectedSticker.dbId}`, {
                    posX: selectedSticker.x,
                    posY: selectedSticker.y,
                    scale: selectedSticker.scale,
                    rotation: selectedSticker.rotation
                });
            } catch (err) {
                console.error("서버 업데이트 실패:", err);
            }
        }
    }

    // ==========================================
    //  [INIT] 초기화 및 팔레트 로직
    // ==========================================

    document.addEventListener('DOMContentLoaded', () => {
        const postId = window.ST_DATA?.postId;

        if (postId) {
            axios.get(`/api/decorations/post/${postId}`).then(async res => {
                stickers = res.data.map(item => ({
                    dbId: item.decorationId,
                    postImageId: item.postImageId,
                    stickerId: item.stickerId,
                    imgUrl: item.stickerImageUrl,
                    x: item.posX,
                    y: item.posY,
                    originX: item.posX, // 💡 초기 위치 저장
                    originY: item.posY,
                    isDirty: false,      // 💡 변경 여부 플래그
                    scale: item.scale || 1.0,
                    rotation: item.rotation || 0,
                    authorLoginId: String(item.authorLoginId || item.loginId || '').trim(),
                    authorNickname: item.nickname || "사용자"
                }));
                console.log("불러온 스티커 목록:", stickers);
                console.log("현재 로그인 유저 ID:", window.ST_DATA?.currentUserId);

                window.INITIAL_STICKER_COUNT = stickers.length;
                await renderStickers();
            });
        }

        // 드롭 시 생성(Create)
        document.querySelectorAll('.sticker-layer').forEach(layer => {
            layer.style.pointerEvents = 'auto';
            layer.addEventListener('dragover', e => e.preventDefault());
            layer.addEventListener('drop', async e => {
                if (!isDecorating) return;
                const imgUrl = e.dataTransfer.getData('imgUrl');
                const stickerId = e.dataTransfer.getData('stickerId');
                const imageId = layer.getAttribute('data-image-id');
                const rect = layer.getBoundingClientRect();

                const newSticker = {
                    postImageId: Number(imageId),
                    stickerId: Number(stickerId),
                    imgUrl: imgUrl,
                    x: ((e.clientX - rect.left) / rect.width) * 100,
                    y: ((e.clientY - rect.top) / rect.height) * 100,
                    scale: 1.0,
                    rotation: 0,
                    authorLoginId: String(window.ST_DATA?.currentUserId || '').trim(),
                    authorNickname: window.ST_DATA?.currentUserNickname || "사용자"
                };
                stickers.push(newSticker);
                selectedSticker = newSticker;
                await renderStickers();
            });
        });

        document.addEventListener('mousedown', (e) => {
            // 클릭한 대상이 스티커 아이템이나 조작 패널이 아닐 경우
            if (!e.target.closest('.sticker-item') && !e.target.closest('.sticker-control-panel')) {
                if (selectedSticker !== null) {
                    selectedSticker = null; // 메모리에서 선택 해제
                    renderStickers();       // 렌더링 엔진을 돌려 패널을 숨김(hidden)
                }
            }
        });

    });


})();