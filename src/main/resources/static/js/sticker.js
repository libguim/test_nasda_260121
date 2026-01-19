/**
 * sticker.js - 생성, 조회, 수정, 삭제(DB 동기화) 완벽 복구 버전
 */
(function() {
    let categories = [];
    let stickersInPalette = [];
    let stickers = [];
    let isDecorating = false;
    let selectedSticker = null;

    // --- [1] 핵심 렌더링: 드래그 이동 & 조작 버튼 & 개별 삭제 ---
    function renderStickers() {
        // 1. 기존 레이어 초기화
        document.querySelectorAll('.sticker-layer').forEach(layer => layer.innerHTML = '');

        stickers.forEach((s) => {
            const targetLayer = document.querySelector(`.sticker-layer[data-image-id="${s.postImageId}"]`);
            if (!targetLayer) return;

            const isSelected = (selectedSticker === s);

            // ✅ 핵심: CSS 고정값 대신 자바스크립트가 계산한 '절대 픽셀' 사용 (기본 92px)
            const absoluteSize = 92 * (s.scale || 1);

            // 2. 컨테이너 생성
            const el = document.createElement('div');
            el.className = `sticker-item absolute transform -translate-x-1/2 -translate-y-1/2 cursor-move ${isSelected ? 'z-[10000]' : 'z-10'}`;

            // ✅ 인라인 스타일로 절대 px값을 강제 주입
            el.style.width = absoluteSize + 'px';
            el.style.height = absoluteSize + 'px';
            el.style.left = s.x + '%';
            el.style.top = s.y + '%';

            // 3. 스티커 이미지 생성
            const img = document.createElement('img');
            img.src = s.imgUrl;
            img.className = 'sticker-main-img';

            // 이미지는 부모(el)의 크기를 100% 채우도록 설정
            Object.assign(img.style, {
                width: '100%',
                height: '100%',
                display: 'block',
                pointerEvents: 'none',
                background: 'transparent',
                objectFit: 'contain'
            });

            if (isSelected) {
                img.style.filter = 'drop-shadow(0 0 10px #fbcfe8)';
                img.style.border = '2.5px dashed #fbcfe8';
                img.style.borderRadius = '12px';
            }

            // transform에서는 크기를 제외하고 위치와 회전만 담당
            el.style.transform = `translate(-50%, -50%) rotate(${s.rotation || 0}deg)`;
            el.appendChild(img);

            // 4 & 5. 삭제 버튼 및 조작 패널 통합
            el.innerHTML += `
            <div class="btn-single-remove ${isSelected ? '' : 'hidden'}"
                 style="position: absolute; top: -12px; right: -12px; width: 28px; height: 28px; 
                        background-color: #ff4d4f; color: white; border: 2px solid white; border-radius: 50%; 
                        display: flex; align-items: center; justify-content: center; font-size: 18px; 
                        font-weight: bold; cursor: pointer; z-index: 10010; pointer-events: auto;">
                ×
            </div>
            <div class="sticker-control-panel absolute -bottom-16 left-1/2 -translate-x-1/2 flex gap-1.5 bg-white/95 p-2 rounded-full shadow-2xl border border-pink-200 z-[10001] pointer-events-auto ${isSelected ? '' : 'hidden'}" 
                 style="min-width: 150px;">
                <button type="button" class="c-btn op-up">➕</button>
                <button type="button" class="c-btn op-down">➖</button>
                <button type="button" class="c-btn op-rotate">🔄</button>
                <button type="button" class="c-btn op-reset">🧹</button>
            </div>
        `;

            // 6. 이벤트 직접 연결 (Swiper 및 10px 조절 대응)
            if (isSelected) {
                const removeBtn = el.querySelector('.btn-single-remove');
                if (removeBtn) {
                    removeBtn.addEventListener('click', (e) => {
                        e.preventDefault(); e.stopPropagation();
                        stickers = stickers.filter(item => item !== s);
                        selectedSticker = null;
                        renderStickers();
                    }, true);
                }

                const panel = el.querySelector('.sticker-control-panel');
                if (panel) {
                    panel.querySelectorAll('button').forEach(btn => {
                        btn.addEventListener('click', (e) => {
                            e.stopPropagation();
                            // 10px 단위 조절을 위해 updateAction 호출
                            if(btn.classList.contains('op-up')) updateAction('scale', 0.1);
                            if(btn.classList.contains('op-down')) updateAction('scale', -0.1);
                            if(btn.classList.contains('op-rotate')) updateAction('rotate', 15);
                            if(btn.classList.contains('op-reset')) updateAction('reset', 0);
                        }, true);
                    });
                }
            }

            // 7. 최적화된 드래그 이벤트 (Swiper 잠금 포함)
            el.onmousedown = (e) => {
                // 1. 예외 처리
                if (!isDecorating || e.target.closest('.sticker-control-panel') || e.target.classList.contains('btn-single-remove')) return;

                e.preventDefault();
                e.stopPropagation();

                // 2. 선택 상태 업데이트 (선택된 적이 없을 때만 전체 렌더링)
                if (selectedSticker !== s) {
                    selectedSticker = s;
                    renderStickers();
                    return; // 렌더링 후 다음 클릭부터 드래그가 시작되도록 하거나, 아래 로직을 계속 실행
                }

                const swiperEl = document.querySelector('.postImagesSwiper');
                const swiperInstance = swiperEl ? swiperEl.swiper : null;
                if (swiperInstance) swiperInstance.allowTouchMove = false;

                const rect = targetLayer.getBoundingClientRect();

                // [핵심 변화] mousemove 시 renderStickers()를 호출하지 않고 style만 변경
                const onMouseMove = (mE) => {
                    // 위치 계산
                    let newX = ((mE.clientX - rect.left) / rect.width) * 100;
                    let newY = ((mE.clientY - rect.top) / rect.height) * 100;

                    // 범위 제한 (0~100%)
                    s.x = Math.max(0, Math.min(100, newX));
                    s.y = Math.max(0, Math.min(100, newY));

                    // ✅ DOM을 다시 그리는 대신 스타일만 즉시 업데이트 (매우 부드러워짐)
                    el.style.left = s.x + '%';
                    el.style.top = s.y + '%';
                };

                const onMouseUp = () => {
                    if (swiperInstance) swiperInstance.allowTouchMove = true;
                    document.removeEventListener('mousemove', onMouseMove);
                    document.removeEventListener('mouseup', onMouseUp);

                    // 드래그가 끝난 시점에 최종 좌표를 데이터에 확정 (필요 시)
                    console.log("드래그 종료:", s.x, s.y);
                };

                document.addEventListener('mousemove', onMouseMove);
                document.addEventListener('mouseup', onMouseUp);
            };

            targetLayer.appendChild(el);
        });
    }

    function updateAction(type, val) {
        if (!selectedSticker) return;

        if (type === 'scale') {
            // 1. 기준 사이즈 설정
            const baseSize = 92;

            // 2. 현재 scale 값을 가져옵니다. (없으면 1.0)
            let currentScale = selectedSticker.scale || 1.0;

            // 3. 10px에 해당하는 scale 변화량을 계산합니다.
            // 92px의 10%는 9.2px이므로, 약 0.108 정도가 10px의 비율입니다.
            // 계산하기 쉽게 10 / 92 값을 더해줍니다.
            const scaleStep = 10 / baseSize;

            if (val > 0) {
                currentScale += scaleStep; // 확대 (+)
            } else {
                currentScale -= scaleStep; // 축소 (-)
            }

            // 4. 최소 scale을 0.4(약 37px)로 제한하여 사라짐 방지
            selectedSticker.scale = Math.max(0.4, currentScale);

        } else if (type === 'rotate') {
            selectedSticker.rotation = ((selectedSticker.rotation || 0) + val) % 360;
        } else if (type === 'reset') {
            selectedSticker.scale = 1.0;
            selectedSticker.rotation = 0;
        }

        // ✅ 변경된 상태로 화면을 즉시 다시 그립니다.
        renderStickers();
    }

    // --- [2] 저장 기능: 삭제 상태 DB 동기화 (가장 중요) ---
    window.saveDecoration = async function() {
        const allImageLayers = Array.from(document.querySelectorAll('.sticker-layer'));
        const allImageIds = allImageLayers.map(l => Number(l.getAttribute('data-image-id')));

        const groups = stickers.reduce((acc, obj) => {
            if (!acc[obj.postImageId]) acc[obj.postImageId] = [];
            acc[obj.postImageId].push(obj);
            return acc;
        }, {});

        try {
            // ✅ 핵심: Promise.all 대신 순서대로(async/await) 하나씩 요청 보냄
            for (const imageId of allImageIds) {
                const layerStickers = groups[imageId] || [];

                // 한 레이어에 대한 저장이 완전히 끝날 때까지 기다립니다.
                await axios.post('/api/decorations', {
                    postImageId: imageId,
                    userId: Number(window.ST_DATA?.currentUserId || 1),
                    decorations: layerStickers.map(s => ({
                        stickerId: s.stickerId,
                        posX: parseFloat(s.x.toFixed(2)),
                        posY: parseFloat(s.y.toFixed(2)),
                        scale: parseFloat((s.scale || 1.0).toFixed(2)),
                        rotation: s.rotation || 0,
                        zIndex: 10
                    }))
                });
                console.log(`이미지 ID ${imageId} 저장 완료`);
            }

            alert("모든 스티커 설정이 저장되었습니다! ✨");
            location.reload();

        } catch (error) {
            console.error("저장 중 오류 발생:", error);
            alert("저장 중 데드락 또는 통신 오류가 발생했습니다. 다시 시도해주세요.");
        }
    };

    window.clearAllStickers = function() {
        if (confirm('모든 스티커를 비우시겠습니까?')) {
            stickers = []; selectedSticker = null; renderStickers();
        }
    };

    // --- [3] 초기화 및 기타 로직 ---
    window.startDecoration = function() {
        isDecorating = true;
        document.querySelectorAll('.sticker-layer').forEach(l => l.style.pointerEvents = 'auto');
        document.getElementById('deco-active-view')?.classList.remove('hidden');
        document.getElementById('deco-start-view')?.classList.add('hidden');
        fetchStickerCategories();
    };

    window.handleStickerError = function(img) {
        const item = img.closest('.palette-item');
        if (item) item.remove();
    };

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
            div.innerHTML = `<img src="${sticker.stickerImageUrl}" onerror="window.handleStickerError(this)" class="w-12 h-12 object-contain pointer-events-none bg-transparent" style="background:transparent !important;">`;
            div.draggable = true;
            div.addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('imgUrl', sticker.stickerImageUrl);
                e.dataTransfer.setData('stickerId', sticker.stickerId);
            });
            palette.appendChild(div);
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        const postId = window.ST_DATA?.postId;
        if (postId) {
            axios.get(`/api/decorations/post/${postId}`).then(res => {
                stickers = res.data.map(item => ({
                    dbId: item.decorationId, postImageId: item.postImageId,
                    stickerId: item.stickerId, imgUrl: item.stickerImageUrl,
                    x: item.posX, y: item.posY, scale: 1.0,
                    rotation: item.rotation, zIndex: item.zIndex, isSaved: true
                }));
                renderStickers();
            });
        }

        document.querySelectorAll('.sticker-layer').forEach(layer => {
            layer.addEventListener('dragover', e => e.preventDefault());
            layer.addEventListener('drop', e => {
                if (!isDecorating) return;
                e.preventDefault();
                const imgUrl = e.dataTransfer.getData('imgUrl');
                const stickerId = e.dataTransfer.getData('stickerId');
                const imageId = layer.getAttribute('data-image-id');
                const rect = layer.getBoundingClientRect();
                if (!imgUrl || !imageId) return;

                stickers.push({
                    postImageId: Number(imageId),
                    stickerId: Number(stickerId),
                    imgUrl: imgUrl,
                    x: ((e.clientX - rect.left) / rect.width) * 100,
                    y: ((e.clientY - rect.top) / rect.height) * 100,
                    scale: 1.0,
                    rotation: 0,
                    isFlipped: false,
                    isSaved: false
                });
                renderStickers();
            });
        });

        document.addEventListener('mousedown', (e) => {
            if (!e.target.closest('.sticker-item') && !e.target.closest('.sticker-control-panel')) {
                selectedSticker = null; renderStickers();
            }
        });
    });
})();